import type { DailyReport } from '../../api/reports'
import { formatDuration } from '../focus/useFocusController'

export function ReportSourceDetails({ source }: { source: DailyReport['sources'][number] }) {
  if (source.status === 'FOCUS_SESSION') {
    return <small>会话计时 · 会话净时长 {formatDuration(source.focusMs ?? 0)}{source.businessDate ? ` · ${source.businessDate}` : ''}{source.taskId ? ' · 关联待办' : ''} · 不检测实际工作；停工需手动暂停或结束 · 不代表任务完成</small>
  }
  if (source.status === 'TASK_COMPLETION') return <small>任务完成事实</small>
  return null
}
