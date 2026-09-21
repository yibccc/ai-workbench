import type { ReactNode } from 'react'

/** A button group (not ARIA tabs): each choice is keyboard reachable using native buttons. */
export function SegmentedControl<T extends string>({ label, value, onChange, options }: {
  label: string; value: T; onChange: (value: T) => void
  options: readonly { value: T; label: ReactNode }[]
}) {
  return <div className="segmented-control" role="group" aria-label={label}>
    {options.map(option => <button key={option.value} type="button" aria-pressed={value === option.value} onClick={() => onChange(option.value)}>{option.label}</button>)}
  </div>
}
