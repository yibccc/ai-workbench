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

export type FocusSoundStatus = 'disabled' | 'ready' | 'paused' | 'closed' | 'error'

function ownsSoundLease(session: FocusSession | null) {
  return session?.controllerId === tabId && !!session.controllerExpiresAt && Date.parse(session.controllerExpiresAt) > Date.now()
}

function unavailableAudioStatus(context: AudioContext): FocusSoundStatus {
  return context.state === 'closed' ? 'closed' : context.state === 'running' ? 'error' : 'paused'
}

function audioFailureMessage(context: AudioContext) {
  return context.state === 'closed' ? '声音上下文已关闭，请点击恢复声音。'
    : context.state === 'running' ? '声音播放失败，请点击恢复声音。' : '声音被浏览器或系统暂停，请点击恢复声音。'
}

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
  const [soundStatus, setSoundStatus] = useState<FocusSoundStatus>('disabled')
  const [soundError, setSoundError] = useState<string | null>(null)
  const [reminderNotice, setReminderNotice] = useState<string | null>(null)
  const [now, setNow] = useState(0)
  const [unverified, setUnverified] = useState(true)
  const sessionRef = useRef<FocusSession | null>(null)
  const busyRef = useRef(false)
  const channelRef = useRef<BroadcastChannel | null>(null)
  const audioRef = useRef<AudioContext | null>(null)
  const soundEnabledRef = useRef(false)
  const alarmSourceRef = useRef<{ source: AudioBufferSourceNode; gain: GainNode; lease: string } | null>(null)
  const alarmActiveRef = useRef(false)
  const alarmSessionRef = useRef<string | null>(null)
  const alarmRevisionRef = useRef(0)
  const playbackPendingRef = useRef(false)
  const lifetimeRef = useRef(0)
  const liveRef = useRef(false)
  const [alarmActive, setAlarmActive] = useState(false)
  const previousReminder = useRef<{ id: string; ordinal: number; phase: FocusSession['phase'] } | null>(null)
  const initialized = useRef(false)
  const endedRef = useRef<FocusSession | null>(null)
  const lastTick = useRef<{ wall: number; mono: number } | null>(null)

  const stopAlarmSource = useCallback(() => {
    const nodes = alarmSourceRef.current
    alarmSourceRef.current = null
    if (!nodes) return
    try { nodes.source.stop() } catch { /* The lease may have already stopped this source. */ }
    nodes.source.disconnect(); nodes.gain.disconnect()
  }, [])

  const stopAlarm = useCallback(() => {
    alarmActiveRef.current = false
    alarmSessionRef.current = null
    alarmRevisionRef.current++
    setAlarmActive(false)
    setSoundError(null)
    stopAlarmSource()
  }, [stopAlarmSource])

  const resumeAudio = useCallback(async (context: AudioContext) => {
    if (context.state === 'closed') throw new Error('closed')
    let timeout: number | undefined
    let stateChanged: (() => void) | undefined
    try {
      if (context.state !== 'running') await new Promise<void>((resolve, reject) => {
        stateChanged = () => {
          if (context.state === 'running') resolve()
          else if (context.state === 'closed') reject(new Error('closed'))
        }
        context.addEventListener('statechange', stateChanged)
        // A browser may leave resume pending while audio remains interrupted.
        // Release the playback guard so the user can retry on the same context.
        timeout = window.setTimeout(() => reject(new Error('paused')), 5000)
        void context.resume().then(() => {
          if (context.state === 'running') resolve()
          else reject(new Error('paused'))
        }, reject)
      })
    } finally {
      window.clearTimeout(timeout)
      if (stateChanged) context.removeEventListener('statechange', stateChanged)
    }
    if (context.state !== 'running') throw new Error('paused')
  }, [])

  const playCompletionAlarm = useCallback(async () => {
    if (!liveRef.current || !alarmActiveRef.current || playbackPendingRef.current) return
    const current = endedRef.current
    if (!current || current.id !== alarmSessionRef.current) return
    if (!soundEnabledRef.current) {
      setSoundError('达标提醒未能发声，请点击启用并试听声音；也可点击“结束”关闭提醒。')
      return
    }
    if (!ownsSoundLease(current)) {
      stopAlarmSource()
      setSoundError(current.controllerId && current.controllerExpiresAt && Date.parse(current.controllerExpiresAt) > Date.now()
        ? '提醒由其他页面负责。可点击“结束”关闭本页提醒。' : '提醒控制权已失效，请点击恢复声音重新检查；也可点击“结束”关闭提醒。')
      return
    }
    const context = audioRef.current
    if (!context || context.state === 'closed') {
      stopAlarmSource(); setSoundStatus('closed')
      setSoundError('声音上下文已关闭，请点击恢复声音；也可点击“结束”关闭提醒。')
      return
    }
    const revision = alarmRevisionRef.current
    const lifetime = lifetimeRef.current
    playbackPendingRef.current = true
    try {
      // Never resume an expired looping source: a suspended audio clock also pauses its stop deadline.
      if (context.state !== 'running') stopAlarmSource()
      await resumeAudio(context)
      const latest = endedRef.current
      if (!liveRef.current || lifetime !== lifetimeRef.current || revision !== alarmRevisionRef.current
        || !alarmActiveRef.current || audioRef.current !== context || latest?.id !== alarmSessionRef.current) return
      if (!ownsSoundLease(latest)) { stopAlarmSource(); setSoundError('提醒控制权已失效，请点击恢复声音重新检查。'); return }
      setSoundStatus('ready'); setSoundError(null)
      const lease = `${latest.controllerGeneration}`
      const stopAt = context.currentTime + Math.max(0, Date.parse(latest.controllerExpiresAt!) - Date.now()) / 1000
      if (alarmSourceRef.current?.lease === lease) {
        // A later stop call replaces the earlier audio-clock deadline without
        // restarting the pulse pattern on every checkpoint renewal.
        alarmSourceRef.current.source.stop(stopAt)
        return
      }
      stopAlarmSource()
      const buffer = context.createBuffer(1, Math.ceil(context.sampleRate * 0.7), context.sampleRate)
      const samples = buffer.getChannelData(0)
      for (let i = 0; i < samples.length; i++) {
        const t = i / context.sampleRate
        if (t < 0.25) samples[i] = Math.sin(2 * Math.PI * 880 * t) * Math.min(1, t / 0.01, (0.25 - t) / 0.01)
      }
      const source = context.createBufferSource(); const gain = context.createGain()
      source.buffer = buffer; source.loop = true; gain.gain.value = 0.09
      source.connect(gain).connect(context.destination)
      const nodes = { source, gain, lease }
      alarmSourceRef.current = nodes
      source.onended = () => {
        source.disconnect(); gain.disconnect()
        if (alarmSourceRef.current === nodes) {
          alarmSourceRef.current = null
          if (liveRef.current && alarmActiveRef.current && !ownsSoundLease(endedRef.current)) {
            setSoundError('提醒控制权已失效，请点击恢复声音重新检查。')
          }
        }
      }
      source.start()
      source.stop(stopAt)
    } catch {
      if (liveRef.current && lifetime === lifetimeRef.current && revision === alarmRevisionRef.current && alarmActiveRef.current) {
        stopAlarmSource(); setSoundStatus(unavailableAudioStatus(context))
        setSoundError(`${audioFailureMessage(context)}也可点击“结束”关闭提醒。`)
      }
    } finally { playbackPendingRef.current = false }
  }, [resumeAudio, stopAlarmSource])

  const startCompletionAlarm = useCallback((next: FocusSession) => {
    if (alarmActiveRef.current) return
    alarmActiveRef.current = true
    alarmSessionRef.current = next.id
    alarmRevisionRef.current++
    setAlarmActive(true)
    void playCompletionAlarm()
  }, [playCompletionAlarm])

  const adopt = useCallback((next: FocusSession | null) => {
    const latest = sessionRef.current ?? endedRef.current
    if (next && latest?.id === next.id && next.version < latest.version) return
    const key = pendingKey(accountId)
    if (next?.requestId && sessionStorage.getItem(key) === next.requestId) sessionStorage.removeItem(key)
    endedRef.current = next?.phase === 'ENDED' ? next : null
    sessionRef.current = next?.phase === 'ENDED' ? null : next
    setSession(next)
    setError(null)
    if (alarmActiveRef.current) {
      if (next?.id !== alarmSessionRef.current) stopAlarm()
      else void playCompletionAlarm()
    }
    channelRef.current?.postMessage({ type: 'refresh' })
  }, [accountId, playCompletionAlarm, stopAlarm])

  const refresh = useCallback(async () => {
    if (!liveRef.current) return
    const lifetime = lifetimeRef.current
    const known = sessionRef.current ?? (alarmActiveRef.current ? endedRef.current : null)
    let next = await fetchCurrentFocus()
    if (!next && known) next = await fetchFocusSession(known.id)
    if (!liveRef.current || lifetime !== lifetimeRef.current) return
    const latest = sessionRef.current ?? endedRef.current
    if (known && known.id !== latest?.id) return
    const key = pendingKey(accountId)
    if (next?.requestId && sessionStorage.getItem(key) === next.requestId) sessionStorage.removeItem(key)
    if (!next && endedRef.current) { setLoading(false); return }
    // A read may arrive after a command in this tab. Keep the newer version.
    if (!next || !latest || next.id !== latest.id || next.version >= latest.version) {
      const settled = latest?.phase !== 'ENDED' && known?.phase !== 'ENDED' && known?.id === next?.id && next?.phase === 'ENDED'
      endedRef.current = next?.phase === 'ENDED' ? next : null
      sessionRef.current = next?.phase === 'ENDED' ? null : next
      setSession(next)
      if (settled && next) {
        if (next.focusMs >= next.targetMs) startCompletionAlarm(next)
        onSettled()
      } else if (alarmActiveRef.current) {
        if (next?.id !== alarmSessionRef.current) stopAlarm()
        else void playCompletionAlarm()
      }
    }
    setLoading(false)
  }, [accountId, onSettled, playCompletionAlarm, startCompletionAlarm, stopAlarm])

  const run = useCallback(async (operation: (current: FocusSession) => Promise<FocusSession>, quietConflict = false, recoverSettlement = false) => {
    const current = sessionRef.current ?? (alarmActiveRef.current ? endedRef.current : null)
    if (!liveRef.current || !current || busyRef.current) return
    const lifetime = lifetimeRef.current
    const stillCurrent = () => liveRef.current && lifetime === lifetimeRef.current && (sessionRef.current ?? endedRef.current)?.id === current.id
    busyRef.current = true; setBusy(true); setError(null)
    try {
      const next = await operation(current)
      if (!stillCurrent()) return
      const wasActive = sessionRef.current?.id === current.id
      adopt(next)
      if (next.phase === 'ENDED' && wasActive) {
        if (!recoverSettlement && next.focusMs >= next.targetMs) startCompletionAlarm(next)
        onSettled()
      }
      return next
    } catch (caught) {
      if (!stillCurrent()) return
      if (recoverSettlement) {
        try {
          const latest = await fetchFocusSession(current.id)
          if (!stillCurrent()) return
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
          if (!stillCurrent()) return
          const wasActive = sessionRef.current?.id === current.id
          adopt(latest)
          if (latest.phase === 'ENDED' && wasActive) {
            if (latest.focusMs >= latest.targetMs) startCompletionAlarm(latest)
            onSettled()
          }
          if (quietConflict) return latest
        } catch { await refresh().catch(() => undefined) }
      }
      if (!quietConflict) setError(caught instanceof Error ? caught.message : '操作失败，请重试')
    } finally { if (liveRef.current && lifetime === lifetimeRef.current) { busyRef.current = false; setBusy(false) } }
  }, [adopt, onSettled, refresh, startCompletionAlarm])

  const start = useCallback(async (input: { title: string; taskId: string | null; projectId: string | null; targetMinutes: number; intervalMinutes: number }) => {
    if (!liveRef.current || busyRef.current || sessionRef.current) return
    busyRef.current = true; setBusy(true); setError(null)
    const lifetime = lifetimeRef.current
    const key = pendingKey(accountId)
    const pendingId = sessionStorage.getItem(key)
    let requestId = pendingId ?? randomUuid()
    sessionStorage.setItem(key, requestId)
    try {
      let next = await startFocus({ ...input, requestId })
      if (!liveRef.current || lifetime !== lifetimeRef.current) return
      // A lost response may refer to a session that was settled elsewhere. Resolve
      // that old request first, then give the user's new start a fresh key.
      if (pendingId && next.phase === 'ENDED' && next.requestId === pendingId) {
        requestId = randomUuid()
        sessionStorage.setItem(key, requestId)
        next = await startFocus({ ...input, requestId })
        if (!liveRef.current || lifetime !== lifetimeRef.current) return
      }
      sessionStorage.removeItem(key)
      adopt(next)
    } catch (caught) {
      if (!liveRef.current || lifetime !== lifetimeRef.current) return
      setError(caught instanceof Error ? caught.message : '开始失败，请重试')
      await refresh().catch(() => undefined)
    } finally { if (liveRef.current && lifetime === lifetimeRef.current) { busyRef.current = false; setBusy(false) } }
  }, [accountId, adopt, refresh])

  const transition = useCallback((action: FocusAction) => run(current => transitionFocus(current, action)), [run])
  const end = useCallback(() => run(endFocus, false, true), [run])
  const clearEnded = useCallback(() => { stopAlarm(); endedRef.current = null; setSession(null) }, [stopAlarm])
  const checkpointNow = useCallback(async () => {
    const result = await run(current => checkpointFocus(current, soundEnabledRef.current ? tabId : null), true)
    if (result) setUnverified(false)
    // A conflict can reveal terminal settlement or a new lease generation. Retry
    // with that authoritative version only while this reminder is still pending.
    if (result?.phase === 'ENDED' && alarmActiveRef.current && soundEnabledRef.current && !ownsSoundLease(result)) {
      await run(current => checkpointFocus(current, tabId), true)
    }
  }, [run])

  const enableSound = useCallback(async () => {
    const lifetime = lifetimeRef.current
    const revision = alarmRevisionRef.current
    const pendingAlarm = alarmActiveRef.current
    try {
      const context = !audioRef.current || audioRef.current.state === 'closed' ? new AudioContext() : audioRef.current
      if (context !== audioRef.current) context.addEventListener('statechange', () => {
        if (!liveRef.current || audioRef.current !== context) return
        if (context.state === 'running') {
          setSoundStatus('ready')
          if (alarmActiveRef.current) void playCompletionAlarm()
          else if (soundEnabledRef.current) setSoundError(null)
        }
        else {
          stopAlarmSource()
          setSoundStatus(context.state === 'closed' ? 'closed' : 'paused')
          if (soundEnabledRef.current) setSoundError(context.state === 'closed' ? '声音上下文已关闭，请点击恢复声音。' : '声音被浏览器或系统暂停，请点击恢复声音。')
        }
      })
      audioRef.current = context
      await resumeAudio(context)
      if (!liveRef.current || lifetime !== lifetimeRef.current || audioRef.current !== context) return
      soundEnabledRef.current = true; setSoundEnabled(true); setSoundStatus('ready'); setSoundError(null)
      if (pendingAlarm && revision !== alarmRevisionRef.current) return
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.frequency.value = 660
      gain.gain.value = 0.08
      oscillator.connect(gain).connect(context.destination)
      oscillator.onended = () => { oscillator.disconnect(); gain.disconnect() }
      oscillator.start(); oscillator.stop(context.currentTime + 0.12)
      await checkpointNow()
      if (!liveRef.current || lifetime !== lifetimeRef.current || revision !== alarmRevisionRef.current && pendingAlarm) return
      if (alarmActiveRef.current) await playCompletionAlarm()
    } catch {
      if (!liveRef.current || lifetime !== lifetimeRef.current || pendingAlarm && revision !== alarmRevisionRef.current) return
      const context = audioRef.current
      setSoundStatus(soundEnabledRef.current && context ? unavailableAudioStatus(context) : 'error')
      setSoundError(soundEnabledRef.current && context
        ? `${audioFailureMessage(context)}视觉提示仍可使用。`
        : '声音未启用，请点击启用并试听声音；视觉提示仍可使用。')
    }
  }, [checkpointNow, playCompletionAlarm, resumeAudio, stopAlarmSource])

  useEffect(() => {
    let live = true
    liveRef.current = true
    const lifetime = ++lifetimeRef.current
    const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(`workbench-focus-${accountId}`)
    channelRef.current = channel
    const onMessage = () => { if (live) void refresh().catch(() => undefined) }
    channel?.addEventListener('message', onMessage)
    void Promise.resolve().then(refresh).catch(caught => { if (live) { setLoading(false); setError(caught instanceof Error ? caught.message : '专注状态读取失败') } })
    const sync = window.setInterval(() => {
      if (!live || busyRef.current) return
      const current = sessionRef.current ?? (alarmActiveRef.current ? endedRef.current : null)
      if (current) {
        void checkpointNow()
      } else void refresh().catch(() => undefined)
    }, 20_000)
    const visible = () => {
      setUnverified(true)
      if (document.visibilityState !== 'visible') { initialized.current = false; setReminderNotice(null) }
      if (sessionRef.current || alarmActiveRef.current) void checkpointNow()
      else void refresh().catch(() => undefined)
    }
    document.addEventListener('visibilitychange', visible)
    return () => {
      live = false; liveRef.current = false; lifetimeRef.current = lifetime + 1; window.clearInterval(sync); document.removeEventListener('visibilitychange', visible)
      channel?.removeEventListener('message', onMessage); channel?.close(); channelRef.current = null
      stopAlarm(); if (audioRef.current) { audioRef.current.onstatechange = null; void audioRef.current.close() }; audioRef.current = null
    }
  }, [accountId, checkpointNow, refresh, stopAlarm])

  useEffect(() => {
    const tick = () => {
      if (!liveRef.current) return
      const wall = Date.now(); const mono = performance.now()
      const last = lastTick.current
      lastTick.current = { wall, mono }
      if (last && (mono - last.mono > 60_000 || Math.abs((wall - last.wall) - (mono - last.mono)) > 2_000)) {
        setUnverified(true)
        void checkpointNow()
      }
      if (alarmActiveRef.current && soundEnabledRef.current && endedRef.current?.controllerExpiresAt
        && Date.parse(endedRef.current.controllerExpiresAt) <= wall) {
        stopAlarmSource(); setSoundError('提醒控制权已失效，请点击恢复声音重新检查。')
      }
      setNow(wall)
    }
    queueMicrotask(tick)
    const timer = window.setInterval(tick, 500)
    return () => window.clearInterval(timer)
  }, [checkpointNow, stopAlarmSource])

  const sessionId = session?.id
  useEffect(() => {
    if (!sessionId) return
    void Promise.resolve().then(() => {
      if (!busyRef.current && sessionRef.current?.id === sessionId) return checkpointNow()
    })
  }, [checkpointNow, sessionId])

  useEffect(() => {
    if (session?.phase !== 'RUNNING') return
    // A single deadline from server time also works when display projection is
    // frozen after a long gap. The server still decides whether to settle.
    const delay = Math.max(0, Date.parse(session.anchorAt) + session.targetMs - session.focusMs - Date.now())
    const timer = window.setTimeout(() => { void checkpointNow() }, delay)
    return () => window.clearTimeout(timer)
  }, [checkpointNow, session])

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
    if (!soundEnabled || !ownsSoundLease(session) || !audioRef.current) return
    const context = audioRef.current
    const lifetime = lifetimeRef.current
    void (async () => {
      try {
        await resumeAudio(context)
        const latest = sessionRef.current
        if (!liveRef.current || lifetime !== lifetimeRef.current || audioRef.current !== context || document.visibilityState !== 'visible'
          || latest?.id !== session.id || latest.phase !== session.phase || latest.reminderOrdinal !== session.reminderOrdinal || !ownsSoundLease(latest)) return
        setSoundStatus('ready'); setSoundError(null)
        const oscillator = context.createOscillator(); const gain = context.createGain()
        oscillator.frequency.value = 880; gain.gain.value = 0.09
        oscillator.connect(gain).connect(context.destination)
        oscillator.onended = () => { oscillator.disconnect(); gain.disconnect() }
        oscillator.start(); oscillator.stop(context.currentTime + 0.2)
      } catch {
        if (liveRef.current && lifetime === lifetimeRef.current && sessionRef.current?.id === session.id
          && sessionRef.current.phase === session.phase && sessionRef.current.reminderOrdinal === session.reminderOrdinal) {
          setSoundStatus(unavailableAudioStatus(context))
          setSoundError(`${audioFailureMessage(context)}视觉提示仍可使用。`)
        }
      }
    })()
  }, [resumeAudio, session, soundEnabled])

  useEffect(() => {
    if (!reminderNotice) return
    const timeout = window.setTimeout(() => setReminderNotice(null), 5000)
    return () => window.clearTimeout(timeout)
  }, [reminderNotice])

  useEffect(() => {
    if (!session || unverified || busyRef.current) return
    if (session.phase === 'RUNNING') {
      if (projectedFocusMs(session, now) >= session.targetMs) {
        void Promise.resolve().then(checkpointNow)
      } else if (document.visibilityState === 'visible' && !session.remindersDismissed && projectedFocusMs(session, now) >= session.nextBreakAtMs) {
        void Promise.resolve().then(() => transition('BREAK_DUE'))
      }
    } else if (document.visibilityState === 'visible' && session.phase === 'MICRO_BREAK' && projectedBreakMs(session, now) >= session.breakMs + session.breakRemainingMs) {
      void Promise.resolve().then(() => transition('BREAK_DONE'))
    }
  }, [checkpointNow, now, session, transition, unverified])

  const soundAction = !soundEnabled ? '启用并试听声音' : soundStatus === 'ready' && !soundError ? '试听声音' : '恢复声音'
  return { session, loading, busy, unverified, error, soundEnabled, soundStatus, soundAction, soundError, reminderNotice, alarmActive, stopAlarm, now: unverified && session ? Date.parse(session.anchorAt) : now, refresh, start, transition, end, clearEnded, enableSound, adopt }
}
