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
  const response = await fetch('/api/status', { signal })
  if (!response.ok) {
    throw new Error(`状态接口返回 ${response.status}`)
  }
  return response.json() as Promise<WorkbenchStatus>
}
