export interface WorkbenchEvent {
  eventId: string
  kind: 'INPUT' | 'REPORT'
  entityId: string
  state: string
  revision: number
  occurredAt: string
}

type Listener = (event?: WorkbenchEvent) => void
const listeners = new Map<string, Set<Listener>>()
const seen = new Set<string>()
const lastRevision = new Map<string, number>()
let socket: WebSocket | null = null
let reconnectTimer: number | null = null
let reconnectAttempt = 0
let fallbackTimer: number | null = null
let reconnectEnabled = true
const STORAGE_KEY = 'ai-workbench.pending.v1'

const key = (kind: string, id: string) => `${kind}:${id}`
const pending = () => {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((item): item is string =>
      typeof item === 'string' && /^(INPUT|REPORT):[0-9a-f-]{36}$/i.test(item)).slice(-100) : []
  }
  catch { return [] }
}
const savePending = (items: string[]) => {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify([...new Set(items)].slice(-100))) }
  catch { /* Realtime remains usable for this page when storage is unavailable. */ }
}

function connect() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return
  reconnectEnabled = true
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  socket = new WebSocket(`${protocol}//${location.host}/ws/events`)
  socket.onopen = () => {
    reconnectAttempt = 0
    lastRevision.clear() // A restarted backend begins a new revision sequence; GET is authoritative.
    listeners.forEach(set => set.forEach(listener => listener()))
  }
  socket.onmessage = (message) => {
    try {
      const event = JSON.parse(message.data) as WorkbenchEvent
      if ((event.kind !== 'INPUT' && event.kind !== 'REPORT') || typeof event.entityId !== 'string'
          || typeof event.eventId !== 'string' || typeof event.revision !== 'number') return
      const entityKey = key(event.kind, event.entityId)
      if (seen.has(event.eventId) || event.revision <= (lastRevision.get(entityKey) ?? -1)) return
      seen.add(event.eventId); if (seen.size > 500) seen.delete(seen.values().next().value ?? '')
      lastRevision.set(entityKey, event.revision)
      listeners.get(entityKey)?.forEach(listener => listener(event))
    } catch { /* Ignore malformed notification; GET remains authoritative. */ }
  }
  socket.onclose = () => {
    socket = null
    if (!reconnectEnabled) return
    const delay = Math.min(30_000, 1_000 * 2 ** reconnectAttempt++)
    reconnectTimer = window.setTimeout(connect, delay)
  }
}

export function trackedIds(kind: WorkbenchEvent['kind']) {
  return pending().filter(item => item.startsWith(`${kind}:`)).map(item => item.slice(kind.length + 1))
}

export function trackEntity(kind: WorkbenchEvent['kind'], id: string, listener: Listener) {
  const entityKey = key(kind, id)
  const set = listeners.get(entityKey) ?? new Set<Listener>()
  set.add(listener); listeners.set(entityKey, set)
  savePending([...pending(), entityKey]); connect(); listener()
  if (fallbackTimer === null) fallbackTimer = window.setInterval(() => {
    listeners.forEach(callbacks => callbacks.forEach(callback => callback()))
  }, 15_000)
  return () => {
    set.delete(listener)
    if (!set.size) listeners.delete(entityKey)
    if (listeners.size === 0 && fallbackTimer !== null) {
      window.clearInterval(fallbackTimer); fallbackTimer = null
    }
  }
}

export function finishTracking(kind: WorkbenchEvent['kind'], id: string) {
  const entityKey = key(kind, id)
  savePending(pending().filter(item => item !== entityKey))
}

export function closeRealtimeForTest() {
  reconnectEnabled = false
  if (reconnectTimer !== null) window.clearTimeout(reconnectTimer)
  reconnectTimer = null
  socket?.close(); socket = null
}
