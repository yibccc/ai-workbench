import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Dialog } from './Dialog'
import { DialogContext, type DialogOptions } from './dialogContext'

export function DialogProvider({ children }: { children: ReactNode }) {
  const [options, setOptions] = useState<DialogOptions | null>(null)
  const resolve = useRef<((confirmed: boolean) => void) | null>(null)
  const show = useCallback((next: DialogOptions) => {
    // A second request cannot replace an in-flight confirmation or cause another write.
    if (resolve.current) return Promise.resolve(false)
    return new Promise<boolean>(done => { resolve.current = done; setOptions(next) })
  }, [])
  const close = (confirmed: boolean) => {
    const done = resolve.current; resolve.current = null; setOptions(null); done?.(confirmed)
  }
  useEffect(() => () => { resolve.current?.(false); resolve.current = null }, [])
  return <DialogContext.Provider value={show}>{children}{options && <Confirmation options={options} onClose={close} />}</DialogContext.Provider>
}

function Confirmation({ options, onClose }: { options: DialogOptions; onClose: (confirmed: boolean) => void }) {
  const [value, setValue] = useState(options.input?.value ?? '')
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current) return
    pending.current = true; setBusy(true); setError(null)
    try { await options.onConfirm?.(value.trim()); onClose(true) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '操作失败，请重试') }
    finally { pending.current = false; setBusy(false) }
  }
  return <Dialog titleId="confirmation-title" descriptionId="confirmation-description" busy={busy} onClose={() => onClose(false)}>
    <form onSubmit={event => void submit(event)} aria-busy={busy}>
      <div className="completion-heading"><span className={`completion-icon ${options.danger ? 'dialog-danger-icon' : ''}`} aria-hidden="true">{options.danger ? '!' : '✎'}</span><h2 id="confirmation-title">{options.title}</h2></div>
      <p id="confirmation-description" className="completion-description dialog-description">{options.description}</p>
      {options.input && <label>{options.input.label}<input autoFocus data-dialog-autofocus required maxLength={options.input.maxLength} value={value} disabled={busy} onChange={event => setValue(event.target.value)} /></label>}
      {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
      <div className="actions completion-actions"><button autoFocus={!options.input} data-dialog-autofocus={!options.input ? true : undefined} type="button" className="secondary" disabled={busy} onClick={() => onClose(false)}>取消</button><button type="submit" className={options.danger ? 'dialog-danger-button' : ''} disabled={busy || (!!options.input && !value.trim())}>{busy ? '正在保存…' : options.confirmLabel ?? '确认'}</button></div>
    </form>
  </Dialog>
}
