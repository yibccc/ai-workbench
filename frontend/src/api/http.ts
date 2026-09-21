

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string; title?: string } | null
    throw Object.assign(new Error(problem?.detail ?? problem?.title ?? `请求失败（${response.status}）`), { status: response.status })
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
