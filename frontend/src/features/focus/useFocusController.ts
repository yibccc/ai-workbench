import { useCallback, useEffect, useRef, useState } from 'react'
import { checkpointFocus, endFocus, fetchCurrentFocus, fetchFocusSession, startFocus, transitionFocus, type FocusAction, type FocusSession } from '../../api/focus'

function randomUuid() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

const tabId = randomUuid()
const pendingKey = (accountId: string) => `ai-workbench.focus-start.${accountId}`

export function projectedFocusMs(session: FocusSession, now = Date.now()) {
  if (session.phase !== 'RUNNING') return session.focusMs
  const delta = Math.max(0, now - Date.parse(session.anchorAt))
  if (delta > 60_000) return session.focusMs
  return Math.min(session.targetMs, session.focusMs + delta)
}

export function projectedBreakMs(session: FocusSession, now = Date.now()) {
  if (session.phase !== 'MICRO_BREAK') return session.breakMs
  const delta = Math.max(0, now - Date.parse(session.anchorAt))
  if (delta > 60_000) return session.breakMs
  return session.breakMs + Math.min(session.breakRemainingMs, delta)
}

export function formatDuration(ms: number) {
  const seconds = Math.floor(Math.max(0, ms) / 1000)
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}

export function useFocusController(accountId: string, onSettled: () => void) {
  const [session, setSession] = useState<FocusSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [soundEnabled, setSoundEnabled] = useState(false)
  const [soundError, setSoundError] = useState<string | null>(null)
  const [reminderNotice, setReminderNotice] = useState<string | null>(null)
  const [now, setNow] = useState(0)
  const [unverified, setUnverified] = useState(true)
  const sessionRef = useRef<FocusSession | null>(null)
  const busyRef = useRef(false)
  const channelRef = useRef<BroadcastChannel | null>(null)
  const audioRef = useRef<AudioContext | null>(null)
  const soundEnabledRef = useRef(false)
  const alarmTimerRef = useRef<number | null>(null)
  const alarmOscillatorRef = useRef<OscillatorNode | null>(null)
  const alarmActiveRef = useRef(false)
  const alarmCanPlayRef = useRef(false)
  const [alarmActive, setAlarmActive] = useState(false)
  const previousReminder = useRef<{ id: string; ordinal: number; phase: FocusSession['phase'] } | null>(null)
  const initialized = useRef(false)
  const endedRef = useRef<FocusSession | null>(null)
  const lastTick = useRef<{ wall: number; mono: number } | null>(null)

  const stopAlarm = useCallback(() => {
    alarmActiveRef.current = false
    alarmCanPlayRef.current = false
    setAlarmActive(false)
    setSoundError(null)
    if (alarmTimerRef.current !== null) window.clearInterval(alarmTimerRef.current)
    alarmTimerRef.current = null
    try { alarmOscillatorRef.current?.stop() } catch { /* The current pulse may have already ended. */ }
    alarmOscillatorRef.current = null
  }, [])

  const playAlarmPulse = useCallback(() => {
    if (!alarmActiveRef.current || !alarmCanPlayRef.current || !soundEnabledRef.current) return
    const context = audioRef.current
    if (!context || context.state !== 'running') {
      soundEnabledRef.current = false; setSoundEnabled(false)
      setSoundError('达标铃声被浏览器暂停。请点击“重新启声”，或点击“结束”关闭提醒。')
      if (alarmTimerRef.current !== null) window.clearInterval(alarmTimerRef.current)
      alarmTimerRef.current = null
      return
    }
    try {
      const oscillator = context.createOscillator(); const gain = context.createGain()
      oscillator.frequency.value = 880
      gain.gain.value = 0.09
      oscillator.connect(gain).connect(context.destination)
      oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); if (alarmOscillatorRef.current === oscillator) alarmOscillatorRef.current = null }
      alarmOscillatorRef.current = oscillator
      oscillator.start(); oscillator.stop(context.currentTime + 0.25)
    } catch {
      soundEnabledRef.current = false; setSoundEnabled(false)
      setSoundError('达标铃声播放失败。请点击“重新启声”，或点击“结束”关闭提醒。')
      if (alarmTimerRef.current !== null) window.clearInterval(alarmTimerRef.current)
      alarmTimerRef.current = null
    }
  }, [])

  const startCompletionAlarm = useCallback((next: FocusSession) => {
    if (alarmActiveRef.current) return
    alarmActiveRef.current = true
    alarmCanPlayRef.current = next.controllerId === tabId && !!next.controllerExpiresAt && Date.parse(next.controllerExpiresAt) > Date.now()
    setAlarmActive(true)
    setSoundError(null)
    if (!alarmCanPlayRef.current) setSoundError('专注已达标；本页未取得声音播放权。请点击“结束”关闭提醒。')
    else if (!soundEnabledRef.current) setSoundError('达标提醒未能发声。可重新启声，或点击“结束”关闭提醒。')
    playAlarmPulse()
    if (alarmCanPlayRef.current && soundEnabledRef.current) alarmTimerRef.current = window.setInterval(playAlarmPulse, 700)
  }, [playAlarmPulse])

  const adopt = useCallback((next: FocusSession | null) => {
    const key = pendingKey(accountId)
    if (next?.requestId && sessionStorage.getItem(key) === next.requestId) sessionStorage.removeItem(key)
    endedRef.current = next?.phase === 'ENDED' ? next : null
    sessionRef.current = next?.phase === 'ENDED' ? null : next
    setSession(next)
    setError(null)
    channelRef.current?.postMessage({ type: 'refresh' })
  }, [accountId])

  const refresh = useCallback(async () => {
    const next = await fetchCurrentFocus()
    const key = pendingKey(accountId)
    if (next?.requestId && sessionStorage.getItem(key) === next.requestId) sessionStorage.removeItem(key)
    if (!next && endedRef.current) { setLoading(false); return }
    // A read may arrive after a command in this tab. Keep the newer version.
    if (!next || !sessionRef.current || next.id !== sessionRef.current.id || next.version >= sessionRef.current.version) {
      sessionRef.current = next
      setSession(next)
    }
    setLoading(false)
  }, [accountId])

  const run = useCallback(async (operation: (current: FocusSession) => Promise<FocusSession>, quietConflict = false, recoverSettlement = false) => {
    const current = sessionRef.current
    if (!current || busyRef.current) return
    busyRef.current = true; setBusy(true); setError(null)
    try {
      const next = await operation(current)
      adopt(next)
      if (next.phase === 'ENDED') {
        if (!recoverSettlement && next.focusMs >= next.targetMs) startCompletionAlarm(next)
        onSettled()
      }
      return next
    } catch (caught) {
      if (recoverSettlement) {
        try {
          const latest = await fetchFocusSession(current.id)
          if (latest.phase === 'ENDED' || latest.version >= current.version) adopt(latest)
          if (latest.phase === 'ENDED') {
            onSettled()
            setReminderNotice('结束请求已提交，已从服务器恢复本次投入。')
            return latest
          }
          setError('结束状态未变，请重试；服务器尚未保存本次结束。')
        } catch {
          setError('无法确认结束是否已提交，请重新读取会话后重试。')
        }
        return
      }
      if ((caught as { status?: number }).status === 409) {
        try {
          const latest = await fetchFocusSession(current.id)
          adopt(latest)
          if (latest.phase === 'ENDED') {
            if (latest.focusMs >= latest.targetMs) startCompletionAlarm(latest)
            onSettled()
          }
          if (quietConflict) return latest
        } catch { await refresh().catch(() => undefined) }
      }
      if (!quietConflict) setError(caught instanceof Error ? caught.message : '操作失败，请重试')
    } finally { busyRef.current = false; setBusy(false) }
  }, [adopt, onSettled, refresh, startCompletionAlarm])

  const start = useCallback(async (input: { title: string; taskId: string | null; projectId: string | null; targetMinutes: number; intervalMinutes: number }) => {
    if (busyRef.current || sessionRef.current) return
    busyRef.current = true; setBusy(true); setError(null)
    const key = pendingKey(accountId)
    const pendingId = sessionStorage.getItem(key)
    let requestId = pendingId ?? randomUuid()
    sessionStorage.setItem(key, requestId)
    try {
      let next = await startFocus({ ...input, requestId })
      // A lost response may refer to a session that was settled elsewhere. Resolve
      // that old request first, then give the user's new start a fresh key.
      if (pendingId && next.phase === 'ENDED' && next.requestId === pendingId) {
        requestId = randomUuid()
        sessionStorage.setItem(key, requestId)
        next = await startFocus({ ...input, requestId })
      }
      sessionStorage.removeItem(key)
      adopt(next)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '开始失败，请重试')
      await refresh().catch(() => undefined)
    } finally { busyRef.current = false; setBusy(false) }
  }, [accountId, adopt, refresh])

  const transition = useCallback((action: FocusAction) => run(current => transitionFocus(current, action)), [run])
  const end = useCallback(() => run(endFocus, false, true), [run])
  const clearEnded = useCallback(() => { endedRef.current = null; setSession(null) }, [])
  const checkpointNow = useCallback(async () => {
    const result = await run(current => checkpointFocus(current, document.visibilityState === 'visible' ? tabId : null), true)
    if (result) setUnverified(false)
  }, [run])

  const enableSound = useCallback(async () => {
    try {
      const context = audioRef.current ?? new AudioContext()
      audioRef.current = context
      await context.resume()
      if (context.state !== 'running') throw new Error('浏览器未允许声音播放')
      soundEnabledRef.current = true; setSoundEnabled(true); setSoundError(null)
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.frequency.value = 660
      gain.gain.value = 0.08
      oscillator.connect(gain).connect(context.destination)
      oscillator.start(); oscillator.stop(context.currentTime + 0.12)
      if (alarmActiveRef.current && alarmCanPlayRef.current && alarmTimerRef.current === null) {
        alarmTimerRef.current = window.setInterval(playAlarmPulse, 700)
      }
      if (alarmActiveRef.current && !alarmCanPlayRef.current) setSoundError('专注已达标；本页未取得声音播放权。请点击“结束”关闭提醒。')
    } catch {
      soundEnabledRef.current = false; setSoundEnabled(false); setSoundError('声音未启用。请检查浏览器声音权限；视觉提示仍可使用。')
    }
  }, [playAlarmPulse])

  useEffect(() => {
    let live = true
    const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(`workbench-focus-${accountId}`)
    channelRef.current = channel
    const onMessage = () => { if (live) void refresh().catch(() => undefined) }
    channel?.addEventListener('message', onMessage)
    void Promise.resolve().then(refresh).catch(caught => { if (live) { setLoading(false); setError(caught instanceof Error ? caught.message : '专注状态读取失败') } })
    const sync = window.setInterval(() => {
      if (!live || busyRef.current) return
      const current = sessionRef.current
      if (current) {
        void checkpointNow()
      } else void refresh().catch(() => undefined)
    }, 20_000)
    const visible = () => {
      setUnverified(true)
      if (document.visibilityState !== 'visible') { initialized.current = false; setReminderNotice(null) }
      if (sessionRef.current) void checkpointNow()
      else void refresh().catch(() => undefined)
    }
    document.addEventListener('visibilitychange', visible)
    return () => {
      live = false; window.clearInterval(sync); document.removeEventListener('visibilitychange', visible)
      channel?.removeEventListener('message', onMessage); channel?.close(); channelRef.current = null
      stopAlarm(); void audioRef.current?.close(); audioRef.current = null
    }
  }, [accountId, checkpointNow, refresh, stopAlarm])

  useEffect(() => {
    const tick = () => {
      const wall = Date.now(); const mono = performance.now()
      const last = lastTick.current
      lastTick.current = { wall, mono }
      if (last && (mono - last.mono > 60_000 || Math.abs((wall - last.wall) - (mono - last.mono)) > 2_000)) {
        setUnverified(true)
        if (document.visibilityState === 'visible') void checkpointNow()
      }
      setNow(wall)
    }
    queueMicrotask(tick)
    const timer = window.setInterval(tick, 500)
    return () => window.clearInterval(timer)
  }, [checkpointNow])

  const sessionId = session?.id
  useEffect(() => {
    if (!sessionId || document.visibilityState !== 'visible') return
    void Promise.resolve().then(() => {
      if (!busyRef.current && sessionRef.current?.id === sessionId) return checkpointNow()
    })
  }, [checkpointNow, sessionId])

  useEffect(() => {
    if (!session) return
    const previous = previousReminder.current
    previousReminder.current = { id: session.id, ordinal: session.reminderOrdinal, phase: session.phase }
    if (!initialized.current) { initialized.current = true; return }
    const breakStarted = previous?.id === session.id && previous.phase === 'RUNNING' && session.phase === 'MICRO_BREAK' && session.reminderOrdinal > previous.ordinal
    const breakEnded = previous?.id === session.id && previous.phase === 'MICRO_BREAK' && session.phase === 'RUNNING' && session.reminderOrdinal === previous.ordinal
    if (!breakStarted && !breakEnded) return
    if (document.visibilityState !== 'visible') return
    queueMicrotask(() => setReminderNotice(breakStarted ? '微休息开始，请闭眼放松 15 秒' : '微休息结束，继续专注'))
    const ownsLease = session.controllerId === tabId && !!session.controllerExpiresAt && Date.parse(session.controllerExpiresAt) > Date.now()
    if (!soundEnabled || !ownsLease || document.visibilityState !== 'visible' || !audioRef.current) return
    const context = audioRef.current
    if (context.state !== 'running') { setSoundError('声音播放已被浏览器暂停；请重新启用声音。'); return }
    try {
      const oscillator = context.createOscillator(); const gain = context.createGain()
      oscillator.frequency.value = 880; gain.gain.value = 0.09
      oscillator.connect(gain).connect(context.destination)
      oscillator.start(); oscillator.stop(context.currentTime + 0.2)
    } catch {
      queueMicrotask(() => { setSoundEnabled(false); setSoundError('声音播放失败，请检查浏览器权限；视觉提示仍可使用。') })
    }
  }, [session, soundEnabled])

  useEffect(() => {
    if (!reminderNotice) return
    const timeout = window.setTimeout(() => setReminderNotice(null), 5000)
    return () => window.clearTimeout(timeout)
  }, [reminderNotice])

  useEffect(() => {
    if (!session || unverified || busyRef.current || document.visibilityState !== 'visible') return
    if (session.phase === 'RUNNING') {
      if (projectedFocusMs(session, now) >= session.targetMs) {
        void Promise.resolve().then(checkpointNow)
      } else if (!session.remindersDismissed && projectedFocusMs(session, now) >= session.nextBreakAtMs) {
        void transition('BREAK_DUE')
      }
    } else if (session.phase === 'MICRO_BREAK' && projectedBreakMs(session, now) >= session.breakMs + session.breakRemainingMs) {
      void transition('BREAK_DONE')
    }
  }, [checkpointNow, now, session, transition, unverified])

  return { session, loading, busy, unverified, error, soundEnabled, soundError, reminderNotice, alarmActive, stopAlarm, now: unverified && session ? Date.parse(session.anchorAt) : now, refresh, start, transition, end, clearEnded, enableSound, adopt }
}
