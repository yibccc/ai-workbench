import { useRef, useState, type FormEvent } from 'react'
import type { Project } from '../../api/projects'
import { createTask, updateTask, type TaskItem, type TaskPriority } from '../../api/tasks'
import { Dialog } from '../../components/Dialog'
import { Icon } from '../../components/Icon'
import { useDialog } from '../../components/dialogContext'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { localDateTime, toInstant } from '../../utils/date'
import { ProjectPicker } from '../projects/ProjectPicker'

export function TaskEditorDialog({ task, projects, onSaved, onClose }: {
  task: TaskItem | null; projects: Project[]; onSaved: () => Promise<void>; onClose: () => void
}) {
  const showDialog = useDialog()
  const [title, setTitle] = useState(task?.title ?? '')
  const [notes, setNotes] = useState(task?.notes ?? '')
  const [projectId, setProjectId] = useState(task?.project?.id ?? '')
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM')
  const [dueAt, setDueAt] = useState(task?.dueAt ? localDateTime(task.dueAt) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pending = useRef(false)
  const archivedSelection = projects.some(project => project.id === projectId && project.status === 'ARCHIVED') && projectId !== task?.project?.id
  const dirty = title !== (task?.title ?? '') || notes !== (task?.notes ?? '') || projectId !== (task?.project?.id ?? '') || priority !== (task?.priority ?? 'MEDIUM') || dueAt !== (task?.dueAt ? localDateTime(task.dueAt) : '')
  useBeforeUnload(dirty)
  const close = async () => {
    if (pending.current) return
    if (!dirty || await showDialog({ title: '放弃未保存的待办？', description: '当前填写的内容尚未保存。', confirmLabel: '放弃修改', danger: true })) onClose()
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current || !title.trim() || archivedSelection) return
    pending.current = true; setBusy(true); setError(null)
    try {
      const input = { projectId: projectId || null, title: title.trim(), notes, dueAt: dueAt ? toInstant(dueAt) : null, priority }
      if (task) await updateTask(task.id, { ...input, version: task.version })
      else await createTask(input)
      await onSaved()
      onClose()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '保存失败，请重试') }
    finally { pending.current = false; setBusy(false) }
  }
  return <Dialog titleId="task-editor-title" busy={busy} className="editor-drawer" onClose={() => void close()}>
    <div className="drawer-heading"><div><p className="kicker">NEXT STEP</p><h2 id="task-editor-title">{task ? '编辑待办' : '新建待办'}</h2></div><button className="icon-button secondary" aria-label="关闭待办编辑" type="button" disabled={busy} onClick={() => void close()}><Icon name="close" /></button></div>
    <p className="drawer-description">先明确下一步，再补充项目、优先级和时间。</p>
    <form onSubmit={event => void submit(event)} aria-busy={busy}><fieldset disabled={busy}>
      <label>待办标题<input autoFocus data-dialog-autofocus data-testid="task-title" required maxLength={240} value={title} onChange={event => setTitle(event.target.value)} onKeyDown={event => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); if (!busy) event.currentTarget.form?.requestSubmit() }
      }} placeholder="下一步要完成什么？" /></label>
      <label className="task-notes">备注 <span className="optional">选填</span><textarea maxLength={4000} rows={4} value={notes} onChange={event => setNotes(event.target.value)} placeholder="补充背景、步骤或验收标准" /></label>
      <div className="form-row"><label>所属项目<ProjectPicker projects={projects} value={projectId} onChange={setProjectId} currentId={task?.project?.id} /></label><label>优先级<select data-testid="task-priority" value={priority} onChange={event => setPriority(event.target.value as TaskPriority)}><option value="HIGH">高优先级</option><option value="MEDIUM">中优先级</option><option value="LOW">低优先级</option></select></label></div>
      <label className="task-notes">截止时间 <span className="optional">选填</span><input type="datetime-local" value={dueAt} onChange={event => setDueAt(event.target.value)} /></label>
      <p className="field-hint">Ctrl / ⌘ + Enter 保存 · Esc 关闭</p>
      {archivedSelection && <p className="inline-notice inline-error" role="alert">当前项目已归档，请切换项目后保存。</p>}
      {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
      <div className="actions completion-actions"><button className="secondary" type="button" disabled={busy} onClick={() => void close()}>取消</button><button type="submit" disabled={busy || !title.trim() || archivedSelection}>{busy ? '正在保存…' : task ? '保存修改' : '创建待办'}</button></div>
    </fieldset></form>
  </Dialog>
}
