import type { CSSProperties } from 'react'

export type IconName = 'notebook' | 'checklist' | 'report' | 'folder' | 'sparkles' | 'plus' | 'arrow-right' | 'arrow-left' | 'chevron-left' | 'chevron-right' | 'calendar' | 'close' | 'check' | 'edit' | 'search' | 'clock' | 'archive' | 'menu' | 'refresh' | 'alert' | 'eye' | 'eye-off'
const paths: Record<IconName, string[]> = {
  notebook: ['M5 3h13a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5V3Z', 'M5 3H4a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h1M8 7h8M8 11h6M8 15h4'],
  checklist: ['M9 5h11M9 12h11M9 19h11', 'm3 5 1 1 2-2m-3 8 1 1 2-2m-3 8 1 1 2-2'],
  report: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z', 'M14 2v6h6M8 12h8M8 16h8'],
  folder: ['M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Zm0 1h18'],
  sparkles: ['m12 3 2.2 6.8L21 12l-6.8 2.2L12 21l-2.2-6.8L3 12l6.8-2.2L12 3Z', 'M20 2v4M18 4h4'],
  plus: ['M12 5v14M5 12h14'], 'arrow-right': ['M4 12h16m-6-6 6 6-6 6'], 'arrow-left': ['M20 12H4m6-6-6 6 6 6'],
  'chevron-left': ['m15 5-7 7 7 7'], 'chevron-right': ['m9 5 7 7-7 7'],
  calendar: ['M8 2v4M16 2v4M3 10h18', 'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z'],
  close: ['m6 6 12 12M6 18 18 6'], check: ['m5 12 4 4L19 6'],
  edit: ['m15 5 4 4M4 20l4-1L20 7a2.8 2.8 0 0 0-4-4L4 15v5Z'],
  search: ['M21 21l-5-5', 'M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z'],
  clock: ['M12 8v4l3 2', 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'],
  archive: ['M3 3h18v5H3V3Zm2 5v13h14V8M9 12h6'], menu: ['M4 6h16M4 12h16M4 18h16'],
  refresh: ['M20 7v5h-5M4 17v-5h5', 'M6 6a8 8 0 0 1 13 2M18 18a8 8 0 0 1-13-2'],
  alert: ['M12 8v5M12 17h.01', 'M10.3 3.9 2 18a2 2 0 0 0 1.7 3h16.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z'],
  eye: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
  'eye-off': ['m3 3 18 18', 'M10.6 5.1A12 12 0 0 1 12 5c6.4 0 10 7 10 7a18 18 0 0 1-3 3.8M6.2 6.2A20 20 0 0 0 2 12s3.6 7 10 7a12 12 0 0 0 5.8-1.8', 'M9.9 9.9a3 3 0 0 0 4.2 4.2'],
}
export function Icon({ name, size = 18, className, style }: { name: IconName; size?: number; className?: string; style?: CSSProperties }) {
  return <svg aria-hidden="true" focusable="false" className={className} style={style} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{paths[name].map((d, i) => <path key={i} d={d} />)}</svg>
}
