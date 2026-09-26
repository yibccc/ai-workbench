import type { DailyReport } from '../../api/reports'
import { formatDuration } from '../focus/useFocusController'

export function ReportSourceDetails({ source }: { source: DailyReport['sources'][number] }) {
  if (source.status === 'FOCUS_SESSION') {
    return <small>专注投入 · 净时长 {formatDuration(source.focusMs ?? 0)}{source.businessDate ? ` · ${source.businessDate}` : ''}{source.taskId ? ' · 关联待办' : ''} · 不代表任务完成</small>
  }
  if (source.status === 'TASK_COMPLETION') return <small>任务完成事实</small>
  return null
}
