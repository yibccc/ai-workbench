

let csrfToken: string | null = null
let csrfPromise: Promise<string> | null = null
let identityEpoch = 0
let identityId: string | null = null
let onUnauthorized: (() => void) | null = null
const activeRequests = new Set<AbortController>()

/** Keep only the server's documented, safe ProblemDetail fields for feature decisions. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly currentVersion?: number
  constructor(status: number, problem: unknown) {
    const value = problem && typeof problem === 'object' ? problem as Record<string, unknown> : {}
    super(typeof value.detail === 'string' ? value.detail : typeof value.title === 'string' ? value.title : `请求失败（${status}）`)
    this.name = 'ApiError'
    this.status = status
    this.code = typeof value.code === 'string' ? value.code : undefined
    this.currentVersion = typeof value.currentVersion === 'number' && Number.isSafeInteger(value.currentVersion) && value.currentVersion >= 0 ? value.currentVersion : undefined
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null) { onUnauthorized = handler }

/** Invalidates responses from a previous account and aborts its outstanding requests. */
export function setRequestIdentity(id: string | null) {
  if (identityId === id) return
  identityId = id
  identityEpoch++
  activeRequests.forEach(controller => controller.abort())
  activeRequests.clear()
  csrfToken = null
  csrfPromise = null
}

export function currentRequestIdentity() { return identityId }

export async function getCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken
  if (!csrfPromise) {
    const epoch = identityEpoch
    const loading = fetch('/api/auth/csrf', { credentials: 'same-origin' })
    .then(async response => {
      if (!response.ok) throw new Error('安全令牌读取失败，请重试')
      const body = await response.json() as { token: string }
      if (epoch !== identityEpoch) throw new DOMException('账号已切换', 'AbortError')
      csrfToken = body.token
      return body.token
    })
    const current = loading.finally(() => { if (csrfPromise === current) csrfPromise = null })
    csrfPromise = current
  }
  return csrfPromise
}

async function decodedRequest<T>(path: string, init: RequestInit | undefined, decode: (response: Response) => Promise<T>): Promise<T> {
  const epoch = identityEpoch
  const controller = new AbortController()
  activeRequests.add(controller)
  const callerAbort = () => controller.abort()
  init?.signal?.addEventListener('abort', callerAbort, { once: true })
  if (init?.signal?.aborted) controller.abort()
  try {
    const method = (init?.method ?? 'GET').toUpperCase()
    const headers = new Headers(init?.headers)
    if (init?.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-XSRF-TOKEN', await getCsrfToken())
    if (epoch !== identityEpoch) throw new DOMException('账号已切换', 'AbortError')
    const response = await fetch(path, { ...init, headers, credentials: 'same-origin', signal: controller.signal })
    if (epoch !== identityEpoch) throw new DOMException('账号已切换', 'AbortError')
    if (!response.ok) {
      // Remove the authenticated view as soon as the server confirms expiry;
      // reading an error body can be delayed or fail independently.
      if (response.status === 401 && identityId && !path.startsWith('/api/auth/login')) onUnauthorized?.()
      const problem: unknown = await response.json().catch(() => null)
      if (epoch !== identityEpoch && response.status !== 401) throw new DOMException('账号已切换', 'AbortError')
      throw new ApiError(response.status, problem)
    }
    if (response.status === 204) return undefined as T
    const body = await decode(response)
    if (epoch !== identityEpoch || controller.signal.aborted) throw new DOMException('账号已切换或请求已取消', 'AbortError')
    return body
  } finally {
    activeRequests.delete(controller)
    init?.signal?.removeEventListener('abort', callerAbort)
  }
}

export function request<T>(path: string, init?: RequestInit): Promise<T> {
  return decodedRequest(path, init, async response => {
    const body = await response.text()
    return (body ? JSON.parse(body) : undefined) as T
  })
}

/** Binary reads use the same Cookie, cancellation, expiry and post-decode epoch checks. */
export function requestBlob(path: string, init?: RequestInit): Promise<Blob> {
  return decodedRequest(path, init, response => response.blob())
}
