import { useState, type ReactNode } from 'react'

/** Mount only on first visit; hiding a visited view preserves drafts and in-flight AI work. */
export function RetainedView({ active, children, id }: { active: boolean; children: ReactNode; id?: string }) {
  const [visited, setVisited] = useState(active)
  if (active && !visited) setVisited(true)
  return visited || active ? <div id={id} hidden={!active}>{children}</div> : null
}
