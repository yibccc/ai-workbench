import { createContext, useContext } from 'react'

export type DialogOptions = {
  title: string; description: string; confirmLabel?: string; danger?: boolean
  className?: string
  input?: { label: string; value: string; maxLength: number }
  onConfirm?: (value: string) => Promise<void>
}
export const DialogContext = createContext<((options: DialogOptions) => Promise<boolean>) | null>(null)
export function useDialog() {
  const show = useContext(DialogContext)
  if (!show) throw new Error('DialogProvider is required')
  return show
}
