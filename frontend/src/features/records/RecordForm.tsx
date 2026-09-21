import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Project } from '../../api/projects'
import { createRecord, updateRecord, type WorkRecord } from '../../api/records'
import { Icon } from '../../components/Icon'
import { Dialog } from '../../components/Dialog'
import { useDialog } from '../../components/dialogContext'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { localDateTime, timeForDate, toInstant } from '../../utils/date'
import { ProjectPicker } from '../projects/ProjectPicker'

export function RecordForm({ projects, date, record, onSaved, onCancel, onBusyChange, onDirtyChange }: {
  projects: Project[]; date: string; record?: WorkRecord
  onSaved: (record: WorkRecord) => Promise<void>; onCancel?: () => void
  onBusyChange?: (busy: boolean) => void; onDirtyChange?: (dirty: boolean) => void
}) {
  const [content, setContent] = useState(record?.content ?? '')
  const [projectId, setProjectId] = useState(record?.project?.id ?? '')
  const [initialOccurredAt, setInitialOccurredAt] = useState(() => record ? localDateTime(record.occurredAt) : timeForDate(date))
  const [occurredAt, setOccurredAt] = useState(initialOccurredAt)
  const [previousDate, setPreviousDate] = useState(date)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const submitting = useRef(false)
  const archivedSelection = projects.some(project => project.id === projectId && project.status === 'ARCHIVED') && projectId !== record?.project?.id
  const dirty = record ? content !== record.content || projectId !== (record.project?.id ?? '') || occurredAt !== localDateTime(record.occurredAt) : content.trim().length > 0 || !!projectId || occurredAt !== initialOccurredAt
  useBeforeUnload(dirty)
  useEffect(() => { onBusyChange?.(busy) }, [busy, onBusyChange])
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  // Date browsing must not silently move a partially written record to another day.
  if (date !== previousDate) {
    setPreviousDate(date)
    if (!record && !dirty) {
      const nextTime = timeForDate(date)
      setOccurredAt(nextTime); setInitialOccurredAt(nextTime)
    }
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (submitting.current || !content.trim() || archivedSelection) return
    submitting.current = true; setBusy(true); setError(null); setMessage(null)
    try {
      const input = { projectId: projectId || null, content: content.trim(), occurredAt: toInstant(occurredAt) }
      const saved = record ? await updateRecord(record.id, input) : await createRecord(input)
      if (!record) {
        const nextTime = timeForDate(date)
        setContent(''); setProjectId(''); setOccurredAt(nextTime); setInitialOccurredAt(nextTime)
      }
      setMessage(record ? '修改已保存' : '工作记录已保存')
      try { await onSaved(saved) } catch { setError('记录已保存，但列表刷新失败。请刷新列表，不要重复提交。') }
    } catch (caught) { setError(caught instanceof Error ? caught.message : '保存失败，请重试') }
    finally { submitting.current = false; setBusy(false) }
  }
  return <form onSubmit={event => void submit(event)} aria-busy={busy} className="record-form" data-testid="record-composer">
    <fieldset disabled={busy}>
      <label>工作内容<textarea autoFocus={!!record} data-dialog-autofocus={record ? true : undefined} data-testid="record-content" required maxLength={4000} rows={3} value={content} onChange={event => setContent(event.target.value)} onKeyDown={event => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); if (!busy) event.currentTarget.form?.requestSubmit() }
      }} placeholder="完成了什么？解决了什么问题？把这次的进展记下来。" /></label>
      <div className="form-row"><label>所属项目<ProjectPicker projects={projects} value={projectId} onChange={setProjectId} currentId={record?.project?.id} /></label><label>发生时间<input required type="datetime-local" value={occurredAt} onChange={event => setOccurredAt(event.target.value)} /></label></div>
      {archivedSelection && <p className="inline-notice inline-error">当前项目已归档，请切换项目后保存。</p>}
      <div className="composer-footer"><span className="field-hint">Ctrl / ⌘ + Enter 保存</span><div className="actions">{onCancel && <button className="secondary" type="button" onClick={onCancel}>取消</button>}<button data-testid="record-submit" disabled={busy || !content.trim() || archivedSelection} type="submit"><Icon name="check" size={16} />{busy ? '正在保存…' : record ? '保存修改' : '保存记录'}</button></div></div>
    </fieldset>
    {error && <p className="inline-notice inline-error" role="alert">{error}</p>}{message && <p className="inline-notice inline-success" role="status">{message}</p>}
  </form>
}

export function RecordEditorDialog({ projects, record, date, onSaved, onClose }: {
  projects: Project[]; record: WorkRecord; date: string; onSaved: (record: WorkRecord) => Promise<void>; onClose: () => void
}) {
  const showDialog = useDialog()
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const close = async () => {
    if (busy) return
    if (!dirty || await showDialog({ title: '放弃这次修改？', description: '工作记录尚未保存，关闭后将丢弃本次修改。', confirmLabel: '放弃修改', danger: true })) onClose()
  }
  return <Dialog titleId="record-editor-title" busy={busy} className="editor-drawer" onClose={() => void close()}>
    <div className="drawer-heading"><div><p className="kicker">WORK LOG</p><h2 id="record-editor-title">编辑工作记录</h2></div><button className="icon-button secondary" type="button" aria-label="关闭记录编辑" disabled={busy} onClick={() => void close()}><Icon name="close" /></button></div>
    <RecordForm projects={projects} date={date} record={record} onBusyChange={setBusy} onDirtyChange={setDirty} onCancel={() => void close()} onSaved={async saved => { await onSaved(saved); onClose() }} />
  </Dialog>
}
