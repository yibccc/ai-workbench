export type ComponentState = 'UP' | 'DOWN' | 'NOT_CONFIGURED' | 'NOT_CHECKED'

export interface ProbeStatus {
  status: ComponentState
  detail: string
}

export interface WorkbenchStatus {
  application: string
  checkedAt: string
  components: Record<string, ProbeStatus>
  versions: Record<string, string>
}

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

export interface Project {
  id: string
  name: string
  status: ProjectStatus
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkRecord {
  id: string
  project: Pick<Project, 'id' | 'name' | 'status'> | null
  content: string
  occurredAt: string
  createdAt: string
  updatedAt: string
}

export interface WorkRecordInput {
  projectId: string | null
  content: string
  occurredAt: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string; title?: string } | null
    throw new Error(problem?.detail ?? problem?.title ?? `请求失败（${response.status}）`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function fetchWorkbenchStatus(signal?: AbortSignal): Promise<WorkbenchStatus> {
  return request<WorkbenchStatus>('/api/status', { signal })
}

export const fetchProjects = (includeArchived = true) => request<Project[]>(`/api/projects?includeArchived=${includeArchived}`)
export const createProject = (name: string) => request<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ name }) })
export const renameProject = (id: string, name: string) => request<Project>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
export const archiveProject = (id: string) => request<Project>(`/api/projects/${id}/archive`, { method: 'POST' })
export const fetchRecords = (date: string) => request<WorkRecord[]>(`/api/records?date=${encodeURIComponent(date)}`)
export const createRecord = (input: WorkRecordInput) => request<WorkRecord>('/api/records', { method: 'POST', body: JSON.stringify(input) })
export const updateRecord = (id: string, input: WorkRecordInput) => request<WorkRecord>(`/api/records/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const deleteRecord = (id: string) => request<void>(`/api/records/${id}`, { method: 'DELETE' })
