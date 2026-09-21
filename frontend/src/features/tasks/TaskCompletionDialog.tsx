import { useRef, useState, type FormEvent } from 'react'
import type { TaskItem } from '../../api/tasks'
import { Dialog } from '../../components/Dialog'

export function TaskCompletionDialog({ task, onSave, onClose }: {
  task: TaskItem; onSave: (result: string) => Promise<void>; onClose: () => void
}) {
  const submitting = useRef(false)
  const [result, setResult] = useState(task.status === 'COMPLETED' ? task.completionResult ?? '' : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const completing = task.status === 'PENDING'

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (submitting.current) return
    submitting.current = true
    setBusy(true); setError(null)
    try { await onSave(result); onClose() }
    catch (caught) { setError(caught instanceof Error ? caught.message : '保存失败，请重试') }
    finally { submitting.current = false; setBusy(false) }
  }

  return <Dialog titleId="completion-title" descriptionId="completion-description" busy={busy} onClose={onClose}>
    <form onSubmit={(event) => void submit(event)} aria-busy={busy}>
      <div className="completion-heading"><span className="completion-icon" aria-hidden="true">✓</span><div><p className="kicker">{completing ? 'TASK COMPLETE' : 'TASK RESULT'}</p><h2 id="completion-title">{completing ? '完成待办' : '修改完成结果'}</h2></div></div>
      <p className="completion-task-title">{task.title}</p>
      <p id="completion-description" className="completion-description">{completing ? '完成后会自动生成一条工作记录，你也可以留下这次的成果。' : '更新的结果会同步到这项待办的完成记录。'}</p>
      <label htmlFor="completion-result">完成结果 <span className="muted">选填</span></label>
      <textarea id="completion-result" autoFocus data-dialog-autofocus rows={5} maxLength={4000} disabled={busy} value={result}
        placeholder="例如：已完成第一章阅读，整理了 3 条笔记。"
        onChange={(event) => setResult(event.target.value)}
        onKeyDown={(event) => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form?.requestSubmit() } }} />
      <div className="completion-hint"><span>Ctrl / ⌘ + Enter 提交 · Esc 取消</span><span>{result.length} / 4000</span></div>
      {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
      <div className="actions completion-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>取消</button><button type="submit" disabled={busy}>{busy ? '正在保存…' : completing ? '确认完成' : '保存结果'}</button></div>
    </form>
  </Dialog>
}
