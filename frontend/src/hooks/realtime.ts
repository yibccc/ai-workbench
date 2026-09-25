import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { getCsrfToken, currentRequestIdentity } from '../api/http'

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
let client: Client | null = null
let fallbackTimer: number | null = null
let connectedIdentity: string | null = null
const LEGACY_KEY = 'ai-workbench.pending.v1'
const storageKey = (identity: string) => `ai-workbench.pending.v2.${identity}`
const key = (kind: string, id: string) => `${kind}:${id}`

function pending(identity = currentRequestIdentity()) {
  if (!identity) return []
  try {
    const value: unknown = JSON.parse(localStorage.getItem(storageKey(identity)) ?? '[]')
    return Array.isArray(value) ? value.filter((item): item is string =>
      typeof item === 'string' && /^(INPUT|REPORT):[0-9a-f-]{36}$/i.test(item)).slice(-100) : []
  } catch { return [] }
}

function savePending(items: string[], identity = currentRequestIdentity()) {
  if (!identity) return
  try { localStorage.setItem(storageKey(identity), JSON.stringify([...new Set(items)].slice(-100))) }
  catch { /* Realtime remains usable when storage is unavailable. */ }
}

function processEvent(message: IMessage, identity: string) {
  if (currentRequestIdentity() !== identity) return
  try {
    const event = JSON.parse(message.body) as WorkbenchEvent
    if ((event.kind !== 'INPUT' && event.kind !== 'REPORT') || typeof event.entityId !== 'string'
        || typeof event.eventId !== 'string' || typeof event.revision !== 'number') return
    const entityKey = key(event.kind, event.entityId)
    if (seen.has(event.eventId) || event.revision <= (lastRevision.get(entityKey) ?? -1)) return
    seen.add(event.eventId); if (seen.size > 500) seen.delete(seen.values().next().value ?? '')
    lastRevision.set(entityKey, event.revision)
    listeners.get(entityKey)?.forEach(listener => listener(event))
  } catch { /* Ignore malformed notification; GET remains authoritative. */ }
}

function connect() {
  const identity = currentRequestIdentity()
  if (!identity || (client?.active && connectedIdentity === identity)) return
  if (client) closeRealtime()
  connectedIdentity = identity
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const next = new Client({
    brokerURL: `${protocol}//${location.host}/ws/events`,
    reconnectDelay: 1000,
    maxReconnectDelay: 30000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      // A temporary CSRF fetch failure must not permanently stop STOMP reconnects.
      while (next.active && currentRequestIdentity() === identity) {
        try { next.connectHeaders = { 'X-XSRF-TOKEN': await getCsrfToken() }; return }
        catch { await new Promise(resolve => window.setTimeout(resolve, 1000)) }
      }
    },
    onConnect: () => {
      if (currentRequestIdentity() !== identity) { void next.deactivate(); return }
      lastRevision.clear() // GET is authoritative after a backend restart.
      next.subscribe('/user/queue/workbench-events', message => processEvent(message, identity))
      listeners.forEach(set => set.forEach(listener => listener()))
    },
  })
  client = next
  next.activate()
}

export function trackedIds(kind: WorkbenchEvent['kind']) {
  return pending().filter(item => item.startsWith(`${kind}:`)).map(item => item.slice(kind.length + 1))
}

export function trackEntity(kind: WorkbenchEvent['kind'], id: string, listener: Listener) {
  const identity = currentRequestIdentity()
  if (!identity) return () => undefined
  const entityKey = key(kind, id)
  const set = listeners.get(entityKey) ?? new Set<Listener>()
  set.add(listener); listeners.set(entityKey, set)
  savePending([...pending(identity), entityKey], identity)
  connect(); listener()
  if (fallbackTimer === null) fallbackTimer = window.setInterval(() => {
    if (currentRequestIdentity() !== connectedIdentity) return
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

export function closeRealtime() {
  const old = client
  client = null; connectedIdentity = null
  if (fallbackTimer !== null) window.clearInterval(fallbackTimer)
  fallbackTimer = null
  listeners.clear(); seen.clear(); lastRevision.clear()
  if (old) void old.deactivate()
  // Legacy global pending IDs must never be restored under another account.
  try { localStorage.removeItem(LEGACY_KEY) } catch { /* Storage may be disabled. */ }
}

export const closeRealtimeForTest = closeRealtime
