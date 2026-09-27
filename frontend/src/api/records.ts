import { type PageResponse } from './pagination'
import { type Project } from './projects'
import { request } from './http'

export interface WorkRecord {
  id: string
  project: Pick<Project, 'id' | 'name' | 'status'> | null
  content: string
  source: 'MANUAL' | 'TASK_COMPLETION' | 'FOCUS_SESSION'
  taskId: string | null
  sessionId?: string | null
  businessDate?: string | null
  focusMs?: number | null
  breakMs?: number | null
  segmentStart?: string | null
  segmentEnd?: string | null
  progress?: string | null
  active: boolean
  completionResult: string
  occurredAt: string
  createdAt: string
  updatedAt: string
}

export interface WorkRecordInput {
  projectId: string | null
  content: string
  occurredAt: string
}

export const fetchRecords = (date: string) => request<WorkRecord[]>(`/api/records?date=${encodeURIComponent(date)}`)

export const fetchRecord = (id: string) => request<WorkRecord>(`/api/records/${id}`)

export const fetchRecordPage = (date: string, page = 0, size = 20, signal?: AbortSignal) => request<PageResponse<WorkRecord>>(`/api/records/page?date=${encodeURIComponent(date)}&page=${page}&size=${size}`, { signal })

export const createRecord = (input: WorkRecordInput) => request<WorkRecord>('/api/records', { method: 'POST', body: JSON.stringify(input) })

export const updateRecord = (id: string, input: WorkRecordInput) => request<WorkRecord>(`/api/records/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteRecord = (id: string) => request<void>(`/api/records/${id}`, { method: 'DELETE' })
