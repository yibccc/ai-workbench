import { useEffect, useRef, type ReactNode } from 'react'

/** Native top layer handles focus trapping and makes the background inert. */
export function Dialog({ titleId, descriptionId, busy, onClose, children, className = '' }: {
  titleId: string; descriptionId?: string; busy: boolean; onClose: () => void; children: ReactNode; className?: string
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = ref.current!
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    element.showModal()
    // React autoFocus may run while the dialog is still closed. Focus after showModal.
    element.querySelector<HTMLElement>('[data-dialog-autofocus], [autofocus]')?.focus()
    return () => { element.close(); if (trigger?.isConnected) trigger.focus() }
  }, [])
  return <dialog ref={ref} className={`workbench-dialog ${className}`} aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>{children}</dialog>
}
