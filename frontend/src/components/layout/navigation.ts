import type { IconName } from '../Icon'

export type PageId = 'records' | 'tasks' | 'focus' | 'reports' | 'projects'
export const navigation: readonly { id: PageId; label: string; icon: IconName }[] = [
  { id: 'records', label: '工作记录', icon: 'notebook' },
  { id: 'tasks', label: '待办任务', icon: 'checklist' },
  { id: 'focus', label: '专注', icon: 'clock' },
  { id: 'reports', label: '工作汇报', icon: 'report' },
  { id: 'projects', label: '项目管理', icon: 'folder' },
]
