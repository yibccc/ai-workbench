import { type ReactNode, useEffect, useState } from 'react'

const VERSION = 'v1'

export function CollapsibleSection({ id, title, summary, defaultOpen, children }: {
  id: string; title: string; summary?: string; defaultOpen: boolean; children: ReactNode
}) {
  const storageKey = `ai-workbench.panel.${VERSION}.${id}`
  const [open, setOpen] = useState(() => {
    try {
      const saved = localStorage.getItem(storageKey)
      return saved === 'open' ? true : saved === 'closed' ? false : defaultOpen
    } catch { return defaultOpen }
  })
  const toggle = () => setOpen(value => {
    const next = !value
    try { localStorage.setItem(storageKey, next ? 'open' : 'closed') } catch { /* Keep in-memory preference. */ }
    return next
  })
  useEffect(() => {
    const reveal = (event: Event) => {
      if ((event as CustomEvent<string>).detail !== id) return
      setOpen(true)
      try { localStorage.setItem(storageKey, 'open') } catch { /* Keep panel open in memory. */ }
    }
    window.addEventListener('ai-workbench:open-panel', reveal)
    return () => window.removeEventListener('ai-workbench:open-panel', reveal)
  }, [id, storageKey])
  return <section className="collapsible-section" data-testid={`section-${id}`}>
    <button className="section-toggle" type="button" aria-expanded={open} onClick={toggle}>
      <span>{open ? '−' : '+'} {title}</span><small>{summary ?? (open ? '收起' : '展开')}</small>
    </button>
    <div className="section-content" hidden={!open}>{children}</div>
  </section>
}
