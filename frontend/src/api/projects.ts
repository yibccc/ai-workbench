import { type PageResponse } from './pagination'
import { request } from './http'

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

export interface Project {
  id: string
  name: string
  status: ProjectStatus
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export const fetchProjects = (includeArchived = true, signal?: AbortSignal) => request<Project[]>(`/api/projects?includeArchived=${includeArchived}`, { signal })

export const fetchProjectPage = (page = 0, size = 20, q = '', includeArchived = true, signal?: AbortSignal) => request<PageResponse<Project>>(`/api/projects/page?includeArchived=${includeArchived}&page=${page}&size=${size}&q=${encodeURIComponent(q)}`, { signal })

export const createProject = (name: string) => request<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ name }) })

export const renameProject = (id: string, name: string) => request<Project>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })

export const archiveProject = (id: string) => request<Project>(`/api/projects/${id}/archive`, { method: 'POST' })
