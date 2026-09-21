import { request } from './http'

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

export async function fetchWorkbenchStatus(signal?: AbortSignal): Promise<WorkbenchStatus> {
  return request<WorkbenchStatus>('/api/status', { signal })
}
