

let csrfToken: string | null = null
let csrfPromise: Promise<string> | null = null
let identityEpoch = 0
let identityId: string | null = null
let onUnauthorized: (() => void) | null = null
const activeRequests = new Set<AbortController>()

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

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
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
      const problem = await response.json().catch(() => null) as { detail?: string; title?: string } | null
      throw Object.assign(new Error(problem?.detail ?? problem?.title ?? `请求失败（${response.status}）`), { status: response.status })
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
    const body = await response.text()
    return (body ? JSON.parse(body) : undefined) as T
  } finally {
    activeRequests.delete(controller)
    init?.signal?.removeEventListener('abort', callerAbort)
  }
}
