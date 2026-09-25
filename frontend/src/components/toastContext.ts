import { createContext, useCallback, useContext, useRef } from 'react'

export type ToastKind = 'success' | 'info' | 'error'
export type ShowToast = (message: string, kind?: ToastKind, page?: string) => void

export const ToastContext = createContext<ShowToast | null>(null)

export function useGlobalToast() {
  const show = useContext(ToastContext)
  if (!show) throw new Error('ToastProvider is missing')
  return show
}

/** A retained, hidden workspace must not surface feedback from an old async request. */
export function useToast<T extends HTMLElement>() {
  const show = useContext(ToastContext)
  const toastRef = useRef<T>(null)
  if (!show) throw new Error('ToastProvider is missing')
  const notify = useCallback<ShowToast>((message, kind = 'info') => {
    if (toastRef.current && !toastRef.current.closest('[hidden]')) show(message, kind)
  }, [show])
  return { notify, toastRef }
}
