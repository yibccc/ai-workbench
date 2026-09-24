import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ToastContext, type ShowToast, type ToastKind } from './toastContext'

type Toast = { id: number; message: string; kind: ToastKind; page: string }

export function ToastProvider({ page, children }: { page: string; children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const nextId = useRef(0)
  const show = useCallback<ShowToast>((message, kind = 'info') => {
    setToast({ id: ++nextId.current, message, kind, page: window.location.hash.slice(1) || 'records' })
  }, [])
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => setToast(current => current?.page === page ? current : null))
    return () => window.cancelAnimationFrame(frame)
  }, [page])
  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(current => current?.id === toast.id ? null : current), 5000)
    return () => window.clearTimeout(timer)
  }, [toast])

  return <ToastContext.Provider value={show}>
    {children}
    {toast?.page === page && createPortal(<div className={`viewport-toast viewport-toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
      <span>{toast.message}</span><button type="button" aria-label="关闭提示" onClick={() => setToast(current => current?.id === toast.id ? null : current)}>关闭</button>
    </div>, document.body)}
  </ToastContext.Provider>
}
