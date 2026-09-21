import { type PageResponse } from './pagination'
import { type Project } from './projects'
import { request } from './http'

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
  completionRecordId: string | null
  completionResult: string
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

export const fetchTask = (id: string) => request<TaskItem>(`/api/tasks/${id}`)

export const fetchTaskPage = (filters: TaskFilters = {}, page = 0, size = 20, signal?: AbortSignal) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.status) params.set('status', filters.status)
  if (filters.projectId) params.set('projectId', filters.projectId)
  if (filters.unassigned) params.set('unassigned', 'true')
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.due && filters.due !== 'ALL') params.set('due', filters.due)
  return request<PageResponse<TaskItem>>(`/api/tasks/page?${params}`, { signal })
}

export const createTask = (input: TaskInput) => request<TaskItem>('/api/tasks', { method: 'POST', body: JSON.stringify(input) })

export const updateTask = (id: string, input: TaskInput & { version: number }) => request<TaskItem>(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteTask = (id: string, version: number) => request<void>(`/api/tasks/${id}?version=${version}`, { method: 'DELETE' })

export const completeTask = (id: string, version: number, result = '') => request<TaskItem>(`/api/tasks/${id}/complete`, { method: 'POST', body: JSON.stringify({ version, result }) })

export const reopenTask = (id: string, version: number) => request<TaskItem>(`/api/tasks/${id}/reopen`, { method: 'POST', body: JSON.stringify({ version }) })

export const updateTaskCompletionResult = (id: string, version: number, result: string) => request<TaskItem>(`/api/tasks/${id}/completion-result`, { method: 'PUT', body: JSON.stringify({ version, result }) })
