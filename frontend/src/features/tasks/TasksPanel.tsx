import { useCallback, useEffect, useMemo, useState } from 'react'
import { completeTask, deleteTask, fetchTask, fetchTaskPage, reopenTask, updateTaskCompletionResult, type TaskDueFilter, type TaskItem, type TaskPriority, type TaskStatus } from '../../api/tasks'
import type { Project } from '../../api/projects'
import { Pagination } from '../../components/Pagination'
import { Icon } from '../../components/Icon'
import { SegmentedControl } from '../../components/SegmentedControl'
import { usePagedList } from '../../hooks/usePagedList'
import { useDialog } from '../../components/dialogContext'
import { WORKBENCH_TIME_ZONE } from '../../utils/date'
import { TaskCompletionDialog } from './TaskCompletionDialog'
import { TaskEditorDialog } from './TaskEditorDialog'

const ignoreListError = () => undefined

export type TaskEditRequest = { id: string; request: number }
const priorityLabel: Record<TaskPriority, string> = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }
export function TasksPanel({ projects, onRecordsChanged, onSummaryChange, refreshKey = 0, editRequest }: {
  projects: Project[]; onRecordsChanged: () => Promise<void>; onSummaryChange?: (summary: string) => void
  refreshKey?: number; editRequest?: TaskEditRequest | null
}) {
  const showDialog = useDialog()
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000)
    return () => window.clearInterval(timer)
  }, [])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [editor, setEditor] = useState<{ task: TaskItem | null } | null>(null)
  const [completionTask, setCompletionTask] = useState<TaskItem | null>(null)
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('PENDING')
  const [projectFilter, setProjectFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<TaskPriority | ''>('')
  const [dueFilter, setDueFilter] = useState<TaskDueFilter>('ALL')
  const [workingId, setWorkingId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const queryTasks = useCallback((signal: AbortSignal) => fetchTaskPage({ status: statusFilter || undefined,
    projectId: projectFilter && projectFilter !== 'UNASSIGNED' ? projectFilter : undefined,
    unassigned: projectFilter === 'UNASSIGNED', priority: priorityFilter || undefined, due: dueFilter,
  }, page, size, signal), [dueFilter, page, priorityFilter, projectFilter, size, statusFilter])
  const refreshToken = useMemo(() => ({ projects, refreshKey }), [projects, refreshKey])
  const { data, loading, error: listError, refresh: load } = usePagedList(queryTasks, ignoreListError, setPage, refreshToken)
  const total = data?.totalElements ?? 0
  const tasks = data?.items ?? []
  useEffect(() => onSummaryChange?.(`${total} 项`), [onSummaryChange, total])
  useEffect(() => {
    if (!editRequest) return
    let cancelled = false
    void fetchTask(editRequest.id).then(task => { if (!cancelled) setEditor({ task }) }).catch((caught: unknown) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : '无法加载待办')
    })
    return () => { cancelled = true }
  }, [editRequest])
  const refreshViews = async () => {
    try { await Promise.all([load(), onRecordsChanged()]) }
    catch { setError('操作已保存，但列表刷新失败。请重新加载，不要重复提交。') }
  }
  const changeStatus = (task: TaskItem) => {
    if (workingId) return
    if (task.status === 'PENDING') { setCompletionTask(task); return }
    setWorkingId(task.id); setError(null)
    void reopenTask(task.id, task.version).then(async () => { setMessage('待办已重开，原完成记录已转为历史'); await refreshViews() })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '重开失败')).finally(() => setWorkingId(null))
  }
  const saveCompletion = async (result: string) => {
    if (!completionTask) return
    if (completionTask.status === 'PENDING') await completeTask(completionTask.id, completionTask.version, result)
    else await updateTaskCompletionResult(completionTask.id, completionTask.version, result)
    setError(null); setMessage(completionTask.status === 'PENDING' ? '待办已完成，并生成工作记录' : '完成结果已更新')
    void refreshViews()
  }
  const clearFilters = () => { setProjectFilter(''); setPriorityFilter(''); setDueFilter('ALL'); setPage(0) }
  return <div className="page tasks-page" data-testid="task-panel">
    <header className="page-header"><div><p className="page-eyebrow">把精力留给下一步</p><h1>待办任务</h1><p className="page-description">集中查看计划，完成后自动沉淀为工作记录。</p></div><button type="button" onClick={() => setEditor({ task: null })}><Icon name="plus" size={17} />新建待办</button></header>
    {(error || message) && !listError && <div className={error ? 'notice error' : 'notice success'} role={error ? 'alert' : 'status'}><span>{error ?? message}</span><button className="text-button" type="button" onClick={() => { setError(null); setMessage(null) }}>关闭</button></div>}
    <section className="task-panel panel" aria-label="待办列表">
      <div className="list-toolbar"><SegmentedControl<TaskStatus | ''> label="任务状态" value={statusFilter} onChange={value => { setStatusFilter(value); setPage(0) }} options={[{ value: 'PENDING', label: '待处理' }, { value: 'COMPLETED', label: '已完成' }, { value: '', label: '全部任务' }]} /><span className="field-hint">当前筛选 · {loading ? '加载中' : `${total} 项`}</span></div>
      <div className="task-filters" aria-label="待办筛选">
        <label>项目<select aria-label="项目筛选" value={projectFilter} onChange={event => { setProjectFilter(event.target.value); setPage(0) }}><option value="">全部项目</option><option value="UNASSIGNED">未归属项目</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select></label>
        <label>优先级<select aria-label="优先级筛选" value={priorityFilter} onChange={event => { setPriorityFilter(event.target.value as TaskPriority | ''); setPage(0) }}><option value="">全部优先级</option><option value="HIGH">高优先级</option><option value="MEDIUM">中优先级</option><option value="LOW">低优先级</option></select></label>
        <label>截止日期<select aria-label="截止日期筛选" value={dueFilter} onChange={event => { setDueFilter(event.target.value as TaskDueFilter); setPage(0) }}><option value="ALL">全部期限</option><option value="OVERDUE">已逾期</option><option value="TODAY">今天到期</option><option value="UPCOMING">之后到期</option><option value="NONE">无期限</option></select></label>
        {(projectFilter || priorityFilter || dueFilter !== 'ALL') && <button className="text-button filter-reset" type="button" onClick={clearFilters}>重置筛选</button>}
      </div>
      <div className="task-list" aria-busy={loading}>
        {listError ? <div className="empty error-state" role="alert"><Icon name="alert" size={24} /><strong>暂时无法读取待办</strong><span>{listError}</span><button className="secondary" type="button" onClick={() => void load()}>重新加载</button></div>
          : loading && tasks.length === 0 ? <div className="loading-state" role="status"><span className="loading-dot" />正在读取待办…</div>
          : tasks.length === 0 ? <div className="empty"><span className="empty-icon"><Icon name="checklist" size={28} /></span><strong>这里暂时没有待办</strong><span>调整筛选，或者为下一步创建一项任务。</span><button className="secondary" type="button" onClick={() => setEditor({ task: null })}><Icon name="plus" size={16} />新建待办</button></div>
          : tasks.map(task => {
            const overdue = !!task.dueAt && task.status === 'PENDING' && new Date(task.dueAt).getTime() < now
            return <article id={`task-${task.id}`} data-testid="task-item" className={`task task-${task.priority.toLowerCase()} ${task.status === 'COMPLETED' ? 'task-completed' : ''}`} key={task.id}>
              <button className="task-check" type="button" disabled={!!workingId} aria-label={`${task.status === 'PENDING' ? '完成任务' : '重开任务'}：${task.title}`} title={task.status === 'PENDING' ? '完成并记录成果' : '重新打开任务'} onClick={() => changeStatus(task)}>{task.status === 'COMPLETED' && <Icon name="check" size={14} />}</button>
              <div className="task-copy"><strong>{task.title}</strong>{task.notes && <p>{task.notes}</p>}<div className="task-meta"><span className={`priority-badge priority-${task.priority.toLowerCase()}`}>{priorityLabel[task.priority]}</span><span className={task.project?.status === 'ARCHIVED' ? 'archived' : ''}><Icon name="folder" size={12} />{task.project ? `${task.project.name}${task.project.status === 'ARCHIVED' ? '（已归档）' : ''}` : '未归属项目'}</span>{task.dueAt && <span className={overdue ? 'overdue' : ''}><Icon name="clock" size={12} />{overdue ? '已逾期 · ' : '截止 '}{new Date(task.dueAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>}</div>{task.completionResult && <p className="completion-result">完成结果：{task.completionResult}</p>}</div>
              <div className="record-actions task-actions">{task.status === 'COMPLETED' && <button className="text-button" type="button" onClick={() => setCompletionTask(task)}>{task.completionResult ? '修改结果' : '补充结果'}</button>}<button className="text-button" type="button" disabled={!!workingId} onClick={() => setEditor({ task })}>编辑</button><button className="text-button danger" type="button" disabled={!!workingId} onClick={() => void showDialog({ title: '删除待办？', description: `删除“${task.title}”后，自动完成记录将转为历史。`, confirmLabel: '确认删除', danger: true, onConfirm: async () => { await deleteTask(task.id, task.version); setError(null); setMessage('待办已删除'); void refreshViews() } })}>删除</button></div>
            </article>
          })}
      </div>
      {!listError && <Pagination page={page} totalPages={data?.totalPages ?? 0} size={size} loading={loading} onPage={setPage} onSize={value => { setSize(value); setPage(0) }} />}
    </section>
    <p className="page-footnote"><Icon name="check" size={14} />完成任务时可填写成果，系统会生成对应工作记录。</p>
    {editor && <TaskEditorDialog key={editor.task?.id ?? 'new'} task={editor.task} projects={projects} onClose={() => setEditor(null)} onSaved={async () => { setMessage(editor.task ? '待办已更新' : '待办已创建'); setError(null); setPage(0); void refreshViews() }} />}
    {completionTask && <TaskCompletionDialog task={completionTask} onSave={saveCompletion} onClose={() => setCompletionTask(null)} />}
  </div>
}
