import { useEffect, useRef, type ReactNode } from 'react'

/** Native modal top layer supplies keyboard focus trapping and background inertness. */
export function Dialog({ titleId, descriptionId, busy, onClose, children }: {
  titleId: string; descriptionId?: string; busy: boolean; onClose: () => void; children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = ref.current!
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    element.showModal()
    return () => { element.close(); if (trigger?.isConnected) trigger.focus() }
  }, [])
  return <dialog ref={ref} className="workbench-dialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>{children}</dialog>
}
