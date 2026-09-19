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

export type TaskPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type TaskStatus = 'PENDING' | 'COMPLETED'
export type TaskDueFilter = 'ALL' | 'OVERDUE' | 'TODAY' | 'UPCOMING' | 'NONE'

export interface TaskItem {
  id: string
  project: Pick<Project, 'id' | 'name' | 'status'> | null
  title: string
  notes: string
  priority: TaskPriority
  status: TaskStatus
  dueAt: string | null
  completedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface TaskInput {
  projectId: string | null
  title: string
  notes: string
  dueAt: string | null
  priority: TaskPriority
}

export interface TaskFilters {
  status?: TaskStatus
  projectId?: string
  unassigned?: boolean
  priority?: TaskPriority
  due?: TaskDueFilter
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
export const fetchTasks = (filters: TaskFilters = {}) => {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.projectId) params.set('projectId', filters.projectId)
  if (filters.unassigned) params.set('unassigned', 'true')
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.due && filters.due !== 'ALL') params.set('due', filters.due)
  const query = params.toString()
  return request<TaskItem[]>(`/api/tasks${query ? `?${query}` : ''}`)
}
export const createTask = (input: TaskInput) => request<TaskItem>('/api/tasks', { method: 'POST', body: JSON.stringify(input) })
export const updateTask = (id: string, input: TaskInput & { version: number }) => request<TaskItem>(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const deleteTask = (id: string, version: number) => request<void>(`/api/tasks/${id}?version=${version}`, { method: 'DELETE' })
