import { request } from './http'
import type { WorkRecord } from './records'

export interface FocusRoutine {
  id: string
  title: string
  projectId: string | null
  weekdays: number[]
  defaultDurationMinutes: number
  enabled: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export type FocusPhase = 'RUNNING' | 'MICRO_BREAK' | 'PAUSED' | 'ENDED'
export type FocusAction = 'PAUSE' | 'RESUME' | 'BREAK_DUE' | 'BREAK_DONE' | 'SKIP_BREAK' | 'DISMISS_REMINDERS'

export interface FocusSession {
  id: string
  requestId: string
  title: string
  taskId: string | null
  projectId: string | null
  targetMs: number
  intervalMs: number
  zoneId: string
  phase: FocusPhase
  version: number
  startedAt: string
  anchorAt: string
  endedAt: string | null
  focusMs: number
  breakMs: number
  pauseMs: number
  resumePhase: FocusPhase | null
  breakRemainingMs: number
  nextBreakAtMs: number
  remindersDismissed: boolean
  reminderOrdinal: number
  controllerId: string | null
  controllerGeneration: number
  controllerExpiresAt: string | null
  progress: string
}

export interface FocusToday {
  date: string
  focusMs: number
  breakMs: number
  sessionCount: number
  records: WorkRecord[]
}

export interface RoutineInput {
  title: string
  projectId: string | null
  weekdays: number[]
  defaultDurationMinutes: number
}

export const fetchRoutines = () => request<FocusRoutine[]>('/api/focus/routines')
export const createRoutine = (input: RoutineInput) => request<FocusRoutine>('/api/focus/routines', { method: 'POST', body: JSON.stringify(input) })
export const updateRoutine = (routine: FocusRoutine, input: RoutineInput) => request<FocusRoutine>(`/api/focus/routines/${routine.id}`, { method: 'PUT', body: JSON.stringify({ ...input, version: routine.version, enabled: routine.enabled }) })
export const toggleRoutine = (routine: FocusRoutine) => request<FocusRoutine>(`/api/focus/routines/${routine.id}/${routine.enabled ? 'disable' : 'enable'}`, { method: 'POST', body: JSON.stringify({ version: routine.version }) })
export const fillToday = () => request<{ date: string; created: unknown[]; blocked: Array<{ routineId: string; reason: string }> }>('/api/focus/routines/fill-today', { method: 'POST', body: '{}' })
export const fetchCurrentFocus = () => request<FocusSession | null>('/api/focus/current')
export const fetchFocusSession = (id: string) => request<FocusSession>(`/api/focus/sessions/${id}`)
export const startFocus = (input: { requestId: string; title: string; taskId: string | null; projectId: string | null; targetMinutes: number; intervalMinutes: number }) => request<FocusSession>('/api/focus/sessions', { method: 'POST', body: JSON.stringify(input) })
export const checkpointFocus = (session: FocusSession, controllerId: string | null) => request<FocusSession>(`/api/focus/sessions/${session.id}/checkpoint`, { method: 'POST', body: JSON.stringify({ version: session.version, controllerId, controllerGeneration: controllerId ? session.controllerGeneration : null }) })
export const transitionFocus = (session: FocusSession, action: FocusAction) => request<FocusSession>(`/api/focus/sessions/${session.id}/transition`, { method: 'POST', body: JSON.stringify({ version: session.version, action }) })
export const endFocus = (session: FocusSession) => request<FocusSession>(`/api/focus/sessions/${session.id}/end`, { method: 'POST', body: JSON.stringify({ version: session.version }) })
export const saveFocusProgress = (session: FocusSession, progress: string) => request<FocusSession>(`/api/focus/sessions/${session.id}/progress`, { method: 'PUT', body: JSON.stringify({ version: session.version, progress }) })
export const fetchFocusToday = () => request<FocusToday>('/api/focus/today')
