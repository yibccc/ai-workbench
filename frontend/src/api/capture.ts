import { type TaskPriority } from './tasks'
import { request } from './http'

export type InputStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'REVERTED'

export interface CaptureInput {
  id: string
  requestId: string
  content: string
  referenceAt: string
  zoneId: string
  status: InputStatus
  errorMessage: string | null
  attemptCount: number
  completedAt: string | null
  records: Array<{ id: string; projectId: string | null; projectName: string | null; content: string; occurredAt: string }>
  tasks: Array<{ id: string; projectId: string | null; projectName: string | null; title: string; notes: string; dueAt: string | null; priority: TaskPriority; version: number }>
}

export const createCaptureInput = (requestId: string, content: string, signal?: AbortSignal) => request<CaptureInput>('/api/inputs', { method: 'POST', body: JSON.stringify({ requestId, content }), signal })

export const fetchCaptureInput = (id: string, signal?: AbortSignal) => request<CaptureInput>(`/api/inputs/${id}`, { signal })

export const retryCaptureInput = (id: string, signal?: AbortSignal) => request<CaptureInput>(`/api/inputs/${id}/retry`, { method: 'POST', signal })

export const revertCaptureInput = (id: string, signal?: AbortSignal) => request<CaptureInput>(`/api/inputs/${id}/revert`, { method: 'POST', signal })
