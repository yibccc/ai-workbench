import { type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  completeTask } from '../../api/tasks'
import { createTask, deleteTask, fetchTask, fetchTaskPage, reopenTask, updateTask, updateTaskCompletionResult, type TaskDueFilter, type TaskItem, type TaskPriority, type TaskStatus } from '../../api/tasks'
import { type Project } from '../../api/projects'
import { Pagination } from '../../components/Pagination'
import { ProjectPicker } from '../projects/ProjectPicker'
import { usePagedList } from '../../hooks/usePagedList'
import { TaskCompletionDialog } from './TaskCompletionDialog'
import { useDialog } from '../../components/dialogContext'

import { WORKBENCH_TIME_ZONE, localDateTime, toInstant } from '../../utils/date'

export function TasksPanel({ projects, onRecordsChanged, onSummaryChange }: {
  projects: Project[]; onRecordsChanged: () => Promise<void>; onSummaryChange?: (summary: string) => void
}) {
  const showDialog = useDialog()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [projectId, setProjectId] = useState('')
  const [dueAt, setDueAt] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM')
  const [editing, setEditing] = useState<TaskItem | null>(null)
  const [completionTask, setCompletionTask] = useState<TaskItem | null>(null)
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('PENDING')
  const [projectFilter, setProjectFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<TaskPriority | ''>('')
  const [dueFilter, setDueFilter] = useState<TaskDueFilter>('ALL')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const queryTasks = useCallback((signal: AbortSignal) => fetchTaskPage({
    status: statusFilter || undefined,
    projectId: projectFilter && projectFilter !== 'UNASSIGNED' ? projectFilter : undefined,
    unassigned: projectFilter === 'UNASSIGNED',
    priority: priorityFilter || undefined,
    due: dueFilter,
    }, page, size, signal), [dueFilter, page, priorityFilter, projectFilter, size, statusFilter])
  const { data, loading, refresh: load } = usePagedList(queryTasks, setError, setPage, projects)
  const tasks = data?.items ?? []
  const totalPages = data?.totalPages ?? 0
  const total = data?.totalElements ?? 0
  useEffect(() => onSummaryChange?.(`${total} 项`), [onSummaryChange, total])

  const reset = () => {
    setTitle(''); setNotes(''); setProjectId(''); setDueAt(''); setPriority('MEDIUM'); setEditing(null)
  }
  const run = async (action: () => Promise<void>, success: string) => {
    setBusy(true); setError(null); setMessage(null)
    try { await action(); setMessage(success) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '操作失败') }
    finally { setBusy(false) }
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const input = { projectId: projectId || null, title, notes, dueAt: dueAt ? toInstant(dueAt) : null, priority }
      if (editing) await updateTask(editing.id, { ...input, version: editing.version })
      else await createTask(input)
      reset(); setPage(0); await load()
    }, editing ? '待办已更新' : '待办已创建')
  }
  const beginEdit = (task: TaskItem) => {
    setEditing(task); setTitle(task.title); setNotes(task.notes); setProjectId(task.project?.id ?? '')
    setDueAt(task.dueAt ? localDateTime(task.dueAt) : ''); setPriority(task.priority)
  }
  useEffect(() => {
    const open = (event: Event) => {
      const id = (event as CustomEvent<string>).detail
      void fetchTask(id).then(beginEdit).catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '无法加载待办'))
    }
    window.addEventListener('ai-workbench:edit-task', open)
    return () => window.removeEventListener('ai-workbench:edit-task', open)
  }, [])
  const changeStatus = (task: TaskItem) => {
    if (task.status === 'PENDING') { setCompletionTask(task); return }
    void run(async () => {
      await reopenTask(task.id, task.version)
      await Promise.all([load(), onRecordsChanged()])
    }, '待办已重开，原完成记录已转为历史')
  }
  const saveCompletion = async (result: string) => {
    if (!completionTask) return
    const task = completionTask
    if (task.status === 'PENDING') await completeTask(task.id, task.version, result)
    else await updateTaskCompletionResult(task.id, task.version, result)
    setError(null)
    setMessage(task.status === 'PENDING' ? '待办已完成，并生成工作记录' : '完成结果已更新')
    void Promise.all([load(), onRecordsChanged()]).catch(() => setError('操作已保存，但列表刷新失败，请刷新页面'))
  }

  return <article className="panel task-panel" data-testid="task-panel">
    <div className="section-heading"><div><p className="kicker">TASKS</p><h2>{editing ? '编辑待办' : '添加待办'}</h2></div><span>{total} 项</span></div>
    {(error || message) && <p className={error ? 'inline-notice inline-error' : 'inline-notice inline-success'} role={error ? 'alert' : 'status'}>{error ?? message}</p>}
    <form onSubmit={submit}>
      <label>标题<input data-testid="task-title" required maxLength={240} value={title} onChange={(event) => setTitle(event.target.value)} onKeyDown={(event) => { if (event.ctrlKey && event.key === 'Enter') event.currentTarget.form?.requestSubmit() }} placeholder="下一步要完成什么？（Ctrl+Enter 提交）" /></label>
      <label className="task-notes">备注<textarea maxLength={4000} rows={2} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="可选：补充背景或验收条件" /></label>
      <div className="task-fields">
        <label>项目<ProjectPicker projects={projects} value={projectId} onChange={setProjectId} currentId={editing?.project?.id} /></label>
        <label>优先级<select data-testid="task-priority" value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label>
        <label>截止时间<input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /></label>
      </div>
      <div className="actions"><button disabled={busy} type="submit">{editing ? '保存修改' : '创建待办'}</button>{editing && <button className="secondary" type="button" onClick={reset}>取消编辑</button>}</div>
    </form>

    <div className="task-filters" aria-label="待办筛选">
      <select aria-label="状态筛选" value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value as TaskStatus | ''); setPage(0) }}><option value="">全部状态</option><option value="PENDING">待处理</option><option value="COMPLETED">已完成</option></select>
      <select aria-label="项目筛选" value={projectFilter} onChange={(event) => { setProjectFilter(event.target.value); setPage(0) }}><option value="">全部项目</option><option value="UNASSIGNED">未分类</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select>
      <select aria-label="优先级筛选" value={priorityFilter} onChange={(event) => { setPriorityFilter(event.target.value as TaskPriority | ''); setPage(0) }}><option value="">全部优先级</option><option value="HIGH">高优先级</option><option value="MEDIUM">中优先级</option><option value="LOW">低优先级</option></select>
      <select aria-label="截止日期筛选" value={dueFilter} onChange={(event) => { setDueFilter(event.target.value as TaskDueFilter); setPage(0) }}><option value="ALL">全部期限</option><option value="OVERDUE">已逾期</option><option value="TODAY">今天到期</option><option value="UPCOMING">之后到期</option><option value="NONE">无期限</option></select>
    </div>

    <div className="task-list">
      {tasks.length === 0 ? <div className="empty"><strong>没有符合条件的待办</strong><span>调整筛选条件，或在上方创建一项。</span></div> : tasks.map((task) => <div id={`task-${task.id}`} data-testid="task-item" className={`task task-${task.priority.toLowerCase()}`} key={task.id}>
        <div className="task-copy"><div className="task-meta"><span>{task.status === 'PENDING' ? '待处理' : '已完成'}</span><span>{task.priority === 'HIGH' ? '高优先级' : task.priority === 'MEDIUM' ? '中优先级' : '低优先级'}</span><span className={task.project?.status === 'ARCHIVED' ? 'archived' : ''}>{task.project ? `${task.project.name}${task.project.status === 'ARCHIVED' ? '（已归档）' : ''}` : '未分类'}</span>{task.dueAt && <span>截止 {new Date(task.dueAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE })}</span>}</div><strong>{task.title}</strong>{task.notes && <p>{task.notes}</p>}</div>
        <div className="record-actions">
          <button className="text-button" type="button" onClick={() => changeStatus(task)}>{task.status === 'PENDING' ? '完成' : '重开'}</button>
          {task.status === 'COMPLETED' && <button className="text-button" type="button" onClick={() => setCompletionTask(task)}>{task.completionResult ? '修改结果' : '补充结果'}</button>}
          <button className="text-button" type="button" onClick={() => beginEdit(task)}>编辑</button>
          <button className="text-button danger" type="button" onClick={() => void showDialog({ title: '删除待办？', description: `删除“${task.title}”后，自动完成记录将转为历史。`, confirmLabel: '确认删除', danger: true, onConfirm: async () => { await deleteTask(task.id, task.version); if (editing?.id === task.id) reset(); setError(null); setMessage('待办已删除'); void Promise.all([load(), onRecordsChanged()]).catch(() => setError('删除已保存，但列表刷新失败，请刷新页面')) } })}>删除</button>
        </div>
      </div>)}
    </div>
    <Pagination page={page} totalPages={totalPages} size={size} loading={loading} onPage={setPage} onSize={(value) => { setSize(value); setPage(0) }} />
    {completionTask && <TaskCompletionDialog task={completionTask} onSave={saveCompletion} onClose={() => setCompletionTask(null)} />}
  </article>
}
