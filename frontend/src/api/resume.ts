import { request, requestBlob } from './http'

export type ResumeMode = 'EDIT_CURRENT' | 'PASTE'
export type CurrentResume = {
  exists: boolean; version: number; markdownText: string | null; sourceKind: 'PASTE' | 'MD_FILE' | null
  originalFile: { id: string; fileName: string; size: number; sha256: string } | null
}
export type ResumeReceipt = { requestId: string; operation: string; state: string; resultVersion: number | null; objectId: string | null; safeFailureCode: string | null }
export type ResumeSave = { mode: ResumeMode; markdownText: string; expectedVersion: number; requestId: string }
export const getResume = (signal?: AbortSignal) => request<CurrentResume>('/api/me/resume', { signal })
export const saveResume = (payload: ResumeSave) => request<ResumeReceipt>('/api/me/resume', { method: 'PUT', body: JSON.stringify(payload) })
export const importResume = (file: File, payload: Omit<ResumeSave, 'mode'>) => {
  const body = new FormData()
  body.set('file', file); body.set('markdownText', payload.markdownText)
  body.set('expectedVersion', String(payload.expectedVersion)); body.set('requestId', payload.requestId)
  return request<ResumeReceipt>('/api/me/resume/import', { method: 'POST', body })
}
export const deleteResume = (payload: { expectedVersion: number; requestId: string }) => request<ResumeReceipt>(`/api/me/resume?${new URLSearchParams({ expectedVersion: String(payload.expectedVersion), requestId: payload.requestId })}`, { method: 'DELETE' })
export const downloadResume = (signal?: AbortSignal) => requestBlob('/api/me/resume/original', { signal })
