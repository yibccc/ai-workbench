import { useRef, useState, type FormEvent } from 'react'
import { changeOwnPassword } from '../../api/auth'
import { Dialog } from '../../components/Dialog'

export function PasswordDialog({ onClose, onChanged }: { onClose: () => void; onChanged: () => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current) return
    if (next !== confirm) { setError('两次输入的新密码不一致'); return }
    pending.current = true; setBusy(true); setError(null)
    try { await changeOwnPassword(current, next); onChanged(); onClose() }
    catch (caught) { setError(caught instanceof Error ? caught.message : '修改失败，请重试') }
    finally { pending.current = false; setBusy(false) }
  }
  return <Dialog titleId="password-title" descriptionId="password-description" busy={busy} onClose={onClose}>
    <form onSubmit={event => void submit(event)} aria-busy={busy}>
      <h2 id="password-title">修改密码</h2><p id="password-description" className="dialog-description">修改后，其他设备上的会话会失效。密码为 8～64 个字符。</p>
      <label>当前密码<input data-dialog-autofocus type="password" autoComplete="current-password" required value={current} onChange={event => setCurrent(event.target.value)} disabled={busy} /></label>
      <label>新密码<input type="password" autoComplete="new-password" required value={next} onChange={event => setNext(event.target.value)} disabled={busy} /></label>
      <label>确认新密码<input type="password" autoComplete="new-password" required value={confirm} onChange={event => setConfirm(event.target.value)} disabled={busy} /></label>
      {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
      <div className="actions completion-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>取消</button><button type="submit" disabled={busy}>{busy ? '正在保存…' : '保存密码'}</button></div>
    </form>
  </Dialog>
}
