import { type PageResponse } from './pagination'
import { request } from './http'

export type ReportStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED'

export type ReportSourceRole = 'DAILY_RECORD' | 'DAILY_TASK' | 'WEEK_RECORD' | 'CURRENT_TASK' | 'NEXT_WEEK_TASK'

export interface DailyReport {
  id: string
  requestId: string
  reportType: 'DAILY' | 'WEEKLY'
  date: string
  periodEnd: string
  status: ReportStatus
  content: string
  errorMessage: string | null
  zoneId: string
  version: number
  editedAt: string | null
  previousReportId: string | null
  manualAdditions: string
  manualEditedAt: string | null
  createdAt: string
  updatedAt: string
  errorCode: string | null
  errorStage: string | null
  sourceCount: number
  sources: Array<{ id: string; type: 'RECORD' | 'TASK'; role: ReportSourceRole; entityId: string; content: string; projectId: string | null; projectName: string | null; status: string; sourceTime: string }>
}

export const createDailyReport = (date: string, requestId: string, signal?: AbortSignal) => request<DailyReport>('/api/reports', { method: 'POST', body: JSON.stringify({ reportType: 'DAILY', date, requestId }), signal })

export const fetchDailyReports = (date: string, signal?: AbortSignal) => request<DailyReport[]>(`/api/reports?date=${encodeURIComponent(date)}`, { signal })

export const fetchReportPage = (reportType: 'DAILY' | 'WEEKLY', date: string, page = 0, size = 20, signal?: AbortSignal) => request<PageResponse<DailyReport>>(`/api/reports/page?reportType=${reportType}&date=${encodeURIComponent(date)}&page=${page}&size=${size}`, { signal })

export const fetchReportSourcePage = (id: string, page = 0, size = 20, signal?: AbortSignal) => request<PageResponse<DailyReport['sources'][number]>>(`/api/reports/${id}/sources/page?page=${page}&size=${size}`, { signal })

export const fetchDailyReport = (id: string, signal?: AbortSignal) => request<DailyReport>(`/api/reports/${id}`, { signal })

export const deleteDailyReport = (id: string, version: number) => request<void>(`/api/reports/${id}?version=${version}`, { method: 'DELETE' })

export const saveDailyReport = (id: string, content: string, version: number, signal?: AbortSignal) => request<DailyReport>(`/api/reports/${id}`, { method: 'PATCH', body: JSON.stringify({ content, version }), signal })

export const createWeeklyReport = (date: string, requestId: string, signal?: AbortSignal) => request<DailyReport>('/api/reports', { method: 'POST', body: JSON.stringify({ reportType: 'WEEKLY', date, requestId }), signal })

export const fetchWeeklyReports = (date: string, signal?: AbortSignal) => request<DailyReport[]>(`/api/reports?reportType=WEEKLY&date=${encodeURIComponent(date)}`, { signal })

export const saveReportManualAdditions = (id: string, manualAdditions: string, version: number, signal?: AbortSignal) => request<DailyReport>(`/api/reports/${id}/manual-additions`, { method: 'PATCH', body: JSON.stringify({ manualAdditions, version }), signal })
