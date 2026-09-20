import { type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  completeTask, createTask, deleteTask, fetchTasks, reopenTask, updateTask, updateTaskCompletionResult,
  type Project, type TaskDueFilter, type TaskItem, type TaskPriority, type TaskStatus,
} from './api'

const WORKBENCH_TIME_ZONE = 'Asia/Shanghai'
const localDateTime = (iso: string) => {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: WORKBENCH_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(iso)).map(({ type, value }) => [type, value]))
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}
const toInstant = (dateTime: string) => new Date(`${dateTime}:00+08:00`).toISOString()

export function TasksPanel({ projects, onRecordsChanged }: { projects: Project[]; onRecordsChanged: () => Promise<void> }) {
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [projectId, setProjectId] = useState('')
  const [dueAt, setDueAt] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM')
  const [editing, setEditing] = useState<TaskItem | null>(null)
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('PENDING')
  const [projectFilter, setProjectFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<TaskPriority | ''>('')
  const [dueFilter, setDueFilter] = useState<TaskDueFilter>('ALL')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => setTasks(await fetchTasks({
    status: statusFilter || undefined,
    projectId: projectFilter && projectFilter !== 'UNASSIGNED' ? projectFilter : undefined,
    unassigned: projectFilter === 'UNASSIGNED',
    priority: priorityFilter || undefined,
    due: dueFilter,
  })), [dueFilter, priorityFilter, projectFilter, statusFilter])

  useEffect(() => {
    let active = true
    void fetchTasks({
      status: statusFilter || undefined,
      projectId: projectFilter && projectFilter !== 'UNASSIGNED' ? projectFilter : undefined,
      unassigned: projectFilter === 'UNASSIGNED',
      priority: priorityFilter || undefined,
      due: dueFilter,
    }).then((value) => { if (active) setTasks(value) }).catch((caught: unknown) => {
      if (active) setError(caught instanceof Error ? caught.message : '无法读取待办')
    })
    return () => { active = false }
  }, [dueFilter, priorityFilter, projectFilter, statusFilter, projects])

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
      reset(); await load()
    }, editing ? '待办已更新' : '待办已创建')
  }
  const beginEdit = (task: TaskItem) => {
    setEditing(task); setTitle(task.title); setNotes(task.notes); setProjectId(task.project?.id ?? '')
    setDueAt(task.dueAt ? localDateTime(task.dueAt) : ''); setPriority(task.priority)
  }
  const changeStatus = (task: TaskItem) => {
    const completing = task.status === 'PENDING'
    const result = completing ? window.prompt('可选：补充本次完成结果', '') : null
    if (completing && result === null) return
    void run(async () => {
      if (completing) await completeTask(task.id, task.version, result ?? '')
      else await reopenTask(task.id, task.version)
      await Promise.all([load(), onRecordsChanged()])
    }, completing ? '待办已完成，并生成工作记录' : '待办已重开，原完成记录已转为历史')
  }
  const supplementResult = (task: TaskItem) => {
    const result = window.prompt('补充或修改完成结果', task.completionResult)
    if (result === null) return
    void run(async () => {
      await updateTaskCompletionResult(task.id, task.version, result)
      await Promise.all([load(), onRecordsChanged()])
    }, '完成结果已更新')
  }

  return <article className="panel task-panel">
    <div className="section-heading"><div><p className="kicker">TASKS</p><h2>{editing ? '编辑待办' : '添加待办'}</h2></div><span>{tasks.length} 项</span></div>
    {(error || message) && <p className={error ? 'inline-notice inline-error' : 'inline-notice inline-success'} role={error ? 'alert' : 'status'}>{error ?? message}</p>}
    <form onSubmit={submit}>
      <label>标题<input required maxLength={240} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="下一步要完成什么？" /></label>
      <label className="task-notes">备注<textarea maxLength={4000} rows={2} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="可选：补充背景或验收条件" /></label>
      <div className="task-fields">
        <label>项目<select value={projectId} onChange={(event) => setProjectId(event.target.value)}><option value="">未分类</option>{projects.map((project) => <option disabled={project.status === 'ARCHIVED' && project.id !== editing?.project?.id} key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select></label>
        <label>优先级<select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label>
        <label>截止时间<input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /></label>
      </div>
      <div className="actions"><button disabled={busy} type="submit">{editing ? '保存修改' : '创建待办'}</button>{editing && <button className="secondary" type="button" onClick={reset}>取消编辑</button>}</div>
    </form>

    <div className="task-filters" aria-label="待办筛选">
      <select aria-label="状态筛选" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as TaskStatus | '')}><option value="">全部状态</option><option value="PENDING">待处理</option><option value="COMPLETED">已完成</option></select>
      <select aria-label="项目筛选" value={projectFilter} onChange={(event) => setProjectFilter(event.target.value)}><option value="">全部项目</option><option value="UNASSIGNED">未分类</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select>
      <select aria-label="优先级筛选" value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value as TaskPriority | '')}><option value="">全部优先级</option><option value="HIGH">高优先级</option><option value="MEDIUM">中优先级</option><option value="LOW">低优先级</option></select>
      <select aria-label="截止日期筛选" value={dueFilter} onChange={(event) => setDueFilter(event.target.value as TaskDueFilter)}><option value="ALL">全部期限</option><option value="OVERDUE">已逾期</option><option value="TODAY">今天到期</option><option value="UPCOMING">之后到期</option><option value="NONE">无期限</option></select>
    </div>

    <div className="task-list">
      {tasks.length === 0 ? <div className="empty"><strong>没有符合条件的待办</strong><span>调整筛选条件，或在上方创建一项。</span></div> : tasks.map((task) => <div id={`task-${task.id}`} className={`task task-${task.priority.toLowerCase()}`} key={task.id}>
        <div className="task-copy"><div className="task-meta"><span>{task.status === 'PENDING' ? '待处理' : '已完成'}</span><span>{task.priority === 'HIGH' ? '高优先级' : task.priority === 'MEDIUM' ? '中优先级' : '低优先级'}</span><span className={task.project?.status === 'ARCHIVED' ? 'archived' : ''}>{task.project ? `${task.project.name}${task.project.status === 'ARCHIVED' ? '（已归档）' : ''}` : '未分类'}</span>{task.dueAt && <span>截止 {new Date(task.dueAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE })}</span>}</div><strong>{task.title}</strong>{task.notes && <p>{task.notes}</p>}</div>
        <div className="record-actions">
          <button className="text-button" type="button" onClick={() => changeStatus(task)}>{task.status === 'PENDING' ? '完成' : '重开'}</button>
          {task.status === 'COMPLETED' && <button className="text-button" type="button" onClick={() => supplementResult(task)}>{task.completionResult ? '修改结果' : '补充结果'}</button>}
          <button className="text-button" type="button" onClick={() => beginEdit(task)}>编辑</button>
          <button className="text-button danger" type="button" onClick={() => { if (window.confirm(`确定删除“${task.title}”？自动完成记录将转为历史。`)) void run(async () => { await deleteTask(task.id, task.version); if (editing?.id === task.id) reset(); await Promise.all([load(), onRecordsChanged()]) }, '待办已删除') }}>删除</button>
        </div>
      </div>)}
    </div>
  </article>
}
