import { useCallback, useEffect, useRef, useState } from 'react'
import type { Project } from '../../api/projects'
import { createRoutine, fetchFocusToday, fetchRoutines, fillToday, saveFocusProgress, toggleRoutine, updateRoutine, type FocusRoutine, type FocusSession, type FocusToday, type RoutineInput } from '../../api/focus'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/toastContext'
import { formatDuration, projectedFocusMs, projectedBreakMs } from './useFocusController'

export interface FocusDraft { taskId: string; title: string; projectId: string | null; targetMinutes: number; nonce: number }
export interface FocusControl {
  session: FocusSession | null; loading: boolean; busy: boolean; unverified: boolean; error: string | null; soundEnabled: boolean; soundStatus: string; soundAction: string; soundError: string | null; reminderNotice: string | null; now: number
  refresh: () => Promise<void>
  start: (input: { title: string; taskId: string | null; projectId: string | null; targetMinutes: number; intervalMinutes: number }) => Promise<void>
  transition: (action: 'PAUSE' | 'RESUME' | 'BREAK_DUE' | 'BREAK_DONE' | 'SKIP_BREAK' | 'DISMISS_REMINDERS') => Promise<FocusSession | undefined>
  end: () => Promise<FocusSession | undefined>
  clearEnded: () => void
  enableSound: () => Promise<void>
  adopt: (session: FocusSession | null) => void
}

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const durationOptions = [15, 25, 45, 60] as const
const emptyRoutine: RoutineInput = { title: '', projectId: null, weekdays: [1, 2, 3, 4, 5, 6, 7], defaultDurationMinutes: 25 }
const phaseLabel = { RUNNING: '专注中', MICRO_BREAK: '微休息', PAUSED: '已暂停', ENDED: '已结束' }

export function FocusPage({ projects, draft, control, onTasksChanged, revision }: {
  projects: Project[]; draft: FocusDraft | null; control: FocusControl; onTasksChanged: () => void; revision: number
}) {
  const { notify, toastRef } = useToast<HTMLDivElement>()
  const [title, setTitle] = useState('')
  const [taskId, setTaskId] = useState<string | null>(null)
  const [projectId, setProjectId] = useState<string | null>(null)
  const [targetMinutes, setTargetMinutes] = useState(45)
  const [durationOpen, setDurationOpen] = useState(false)
  const [durationIndex, setDurationIndex] = useState(-1)
  const durationInputRef = useRef<HTMLInputElement>(null)
  const suppressDurationFocus = useRef(false)
  const [intervalMinutes, setIntervalMinutes] = useState(10)
  const [progress, setProgress] = useState('')
  const [routines, setRoutines] = useState<FocusRoutine[]>([])
  const [today, setToday] = useState<FocusToday | null>(null)
  const [routineEdit, setRoutineEdit] = useState<FocusRoutine | null>(null)
  const [routineInput, setRoutineInput] = useState<RoutineInput>(emptyRoutine)
  const [routineBusy, setRoutineBusy] = useState(false)
  const [dataError, setDataError] = useState<string | null>(null)
  const [tab, setTab] = useState<'timer' | 'routines' | 'today'>('timer')
  const active = control.session && control.session.phase !== 'ENDED' ? control.session : null

  useEffect(() => {
    if (!draft) return
    queueMicrotask(() => {
      setTitle(draft.title); setTaskId(draft.taskId); setProjectId(draft.projectId); setTargetMinutes(draft.targetMinutes)
      setTab('timer')
    })
  }, [draft])

  const reload = useCallback(async () => {
    try {
      const [rules, summary] = await Promise.all([fetchRoutines(), fetchFocusToday()])
      setRoutines(rules); setToday(summary); setDataError(null)
    } catch (caught) { setDataError(caught instanceof Error ? caught.message : '数据读取失败') }
  }, [])
  useEffect(() => { void Promise.resolve().then(reload) }, [reload, revision])

  const submitRoutine = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!routineInput.title.trim() || routineInput.weekdays.length === 0) { notify('请输入名称并至少选择一天', 'error'); return }
    setRoutineBusy(true)
    try {
      if (routineEdit) await updateRoutine(routineEdit, routineInput)
      else await createRoutine(routineInput)
      setRoutineEdit(null); setRoutineInput(emptyRoutine)
      await reload(); notify('重复规则已保存', 'success')
    } catch (caught) { notify(caught instanceof Error ? caught.message : '规则保存失败', 'error') }
    finally { setRoutineBusy(false) }
  }
  const toggle = async (routine: FocusRoutine) => {
    setRoutineBusy(true)
    try { await toggleRoutine(routine); await reload() }
    catch (caught) { notify(caught instanceof Error ? caught.message : '启停失败', 'error') }
    finally { setRoutineBusy(false) }
  }
  const refill = async () => {
    setRoutineBusy(true)
    try {
      const result = await fillToday()
      onTasksChanged(); await reload()
      notify(result.blocked.length ? `今日任务已检查；${result.blocked.length} 条规则因项目归档等原因未生成` : '今日重复任务已检查', result.blocked.length ? 'info' : 'success')
    } catch (caught) { notify(caught instanceof Error ? caught.message : '今日任务补齐失败', 'error') }
    finally { setRoutineBusy(false) }
  }
  const saveProgress = async () => {
    if (!control.session || control.session.phase !== 'ENDED') return
    try { control.adopt(await saveFocusProgress(control.session, progress)); await reload(); notify('进展已保存', 'success') }
    catch (caught) { notify(caught instanceof Error ? caught.message : '进展保存失败', 'error') }
  }
  const focusMs = active ? projectedFocusMs(active, control.now) : 0
  const remainingMs = active ? Math.max(0, active.targetMs - focusMs) : 0
  const breakRemaining = active ? Math.max(0, active.breakRemainingMs - (projectedBreakMs(active, control.now) - active.breakMs)) : 0
  const restoreDurationFocus = () => {
    if (document.activeElement !== durationInputRef.current) {
      suppressDurationFocus.current = true
      durationInputRef.current?.focus()
    }
  }
  const chooseDuration = (minutes: number) => {
    setTargetMinutes(minutes); setDurationOpen(false); setDurationIndex(-1)
    restoreDurationFocus()
  }
  const prepareNewFocus = () => {
    const queued = draft && draft.taskId !== control.session?.taskId && taskId === draft.taskId ? draft : null
    setTitle(queued?.title ?? ''); setTaskId(queued?.taskId ?? null); setProjectId(queued?.projectId ?? null)
    setTargetMinutes(queued?.targetMinutes ?? 45); setIntervalMinutes(10)
    setDurationOpen(false); setDurationIndex(-1); setProgress('')
    control.clearEnded()
  }
  return <div ref={toastRef} className="page focus-page" data-testid="focus-page">
    <header className="page-header"><div><p className="page-eyebrow">记录会话时长</p><h1>专注</h1><p className="page-description">切换页面或设备睡眠时仍会计时；停工时请手动暂停或结束。净时长排除暂停与微休息。</p></div></header>
    <div className="focus-tabs" role="tablist" aria-label="专注页面内容">
      {([['timer', '计时'], ['routines', '重复规则'], ['today', '今日汇总']] as const).map(([id, label]) => <button key={id} type="button" role="tab" aria-selected={tab === id} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    <div className="workspace-scroll focus-scroll" role="region" aria-label="专注内容" tabIndex={0}>
      {control.error && <p className="inline-notice inline-error" role="alert">{control.error} <button type="button" className="text-button" onClick={() => void control.refresh()}>重新读取</button></p>}
      {dataError && <p className="inline-notice inline-error" role="alert">{dataError} <button type="button" className="text-button" onClick={() => void reload()}>重新读取</button></p>}
      {tab === 'timer' && <section className="focus-grid focus-timer-grid" aria-label="专注计时">
        <div className="panel focus-main">
          {control.loading ? <p role="status">正在读取当前专注…</p> : active ? <>
            {draft && draft.taskId !== active.taskId && <p className="inline-notice" role="status">当前仍在进行“{active.title}”。新带入的待办会在本次结束后用于下一段专注。</p>}
            <p className="focus-status"><span className="status-dot" />{phaseLabel[active.phase]}</p>
            <h2>{active.title}</h2>
            {control.unverified && <p className="inline-notice" role="status">正在同步服务端计时…</p>}
            {active.phase === 'MICRO_BREAK' ? <><p className="focus-clock" aria-live="off">{formatDuration(breakRemaining)}</p><p className="muted">闭眼放松 15 秒。这里仅作引导，不检测您的状态。</p></>
              : <><p className="focus-clock" aria-live="off">{formatDuration(remainingMs)}</p><p className="muted">已累计会话净时长 {formatDuration(active.focusMs)} / 目标 {formatDuration(active.targetMs)}</p></>}
            <div className="focus-actions">
              {active.phase === 'RUNNING' && <button type="button" disabled={control.busy || control.unverified} onClick={() => void control.transition('PAUSE')}>暂停</button>}
              {active.phase === 'PAUSED' && <button type="button" disabled={control.busy || control.unverified} onClick={() => void control.transition('RESUME')}>继续</button>}
              {active.phase === 'MICRO_BREAK' && <button type="button" disabled={control.busy || control.unverified} onClick={() => void control.transition('SKIP_BREAK')}>跳过本次休息</button>}
              {!active.remindersDismissed && <button className="secondary" type="button" disabled={control.busy || control.unverified} onClick={() => void control.transition('DISMISS_REMINDERS')}>关闭本段提醒</button>}
              <button className="secondary" type="button" disabled={control.busy || control.unverified} onClick={() => void control.end()}>提前结束并保存投入</button>
            </div>
            <div className="focus-sound-controls"><button type="button" className="text-button" onClick={() => void control.enableSound()}>{control.soundAction}</button>{control.soundEnabled && control.soundStatus === 'ready' && <span className="field-hint">声音已启用</span>}</div>
            {control.soundError && <p role="alert" className="inline-notice inline-error">{control.soundError}</p>}
            <p className="field-hint">任务状态不会因结束专注而自动变为完成。</p>
          </> : control.session?.phase === 'ENDED' ? <><h2>本次专注已结束</h2><p className="muted">可在下方补充本次实际进展，或明确开始新的一段。</p></> : <>
            <h2>开始一段专注</h2><p className="muted">选择待办可带入目标，也可以直接填写临时目标。点击开始后才创建会话。</p>
            <form className="focus-form" onSubmit={event => { event.preventDefault(); void control.enableSound(); void control.start({ title: title.trim(), taskId, projectId, targetMinutes, intervalMinutes }) }}>
              <label>目标<input required maxLength={200} value={title} onChange={event => { setTitle(event.target.value); setTaskId(null) }} placeholder="这段时间要推进什么？" /></label>
              {taskId && <p className="focus-task-link">已关联待办 <button type="button" className="text-button" onClick={() => setTaskId(null)}>取消关联</button></p>}
              <label>项目<select value={projectId ?? ''} onChange={event => setProjectId(event.target.value || null)}><option value="">未归属项目</option>{projects.filter(project => project.status === 'ACTIVE' || project.id === projectId).map(project => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select></label>
              <div className="focus-form-row"><div className="focus-duration-field"><label htmlFor="focus-target-minutes">目标净时长（分钟）</label><div className="focus-duration-picker" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDurationOpen(false) }} onKeyDown={event => { if (event.key === 'Escape' && durationOpen) { event.preventDefault(); setDurationOpen(false); setDurationIndex(-1); restoreDurationFocus() } }}><input id="focus-target-minutes" ref={durationInputRef} type="number" min={1} max={480} required role="combobox" aria-haspopup="listbox" aria-expanded={durationOpen} aria-controls="focus-duration-options" aria-activedescendant={durationIndex >= 0 && durationOpen ? `focus-duration-${durationOptions[durationIndex]}` : undefined} value={targetMinutes} onFocus={() => { if (suppressDurationFocus.current) suppressDurationFocus.current = false; else setDurationOpen(true) }} onClick={() => setDurationOpen(true)} onChange={event => { setTargetMinutes(Number(event.target.value)); setDurationIndex(-1) }} onKeyDown={event => { if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); setDurationOpen(true); setDurationIndex(index => event.key === 'ArrowDown' ? Math.min(index + 1, durationOptions.length - 1) : index < 0 ? durationOptions.length - 1 : Math.max(0, index - 1)) } else if (event.key === 'Enter' && durationOpen && durationIndex >= 0) { event.preventDefault(); chooseDuration(durationOptions[durationIndex]) } }} />{durationOpen && <div id="focus-duration-options" className="focus-duration-options" role="listbox" aria-label="快捷时长">{durationOptions.map(minutes => <button id={`focus-duration-${minutes}`} key={minutes} role="option" aria-selected={targetMinutes === minutes} type="button" onClick={() => chooseDuration(minutes)}>{minutes} 分钟</button>)}</div>}</div></div><label>提醒间隔（分钟）<input type="number" min={1} max={120} required value={intervalMinutes} onChange={event => setIntervalMinutes(Number(event.target.value))} /></label></div>
              <div className="focus-actions"><button type="submit" disabled={control.busy || !title.trim()}><Icon name="clock" size={16} />开始专注</button></div>
            </form>
          </>}
        </div>
        {control.session?.phase === 'ENDED' && <section className="panel focus-progress"><h2>本次会话已保存</h2><p>会话净时长 {formatDuration(control.session.focusMs)}，休息 {formatDuration(control.session.breakMs)}。关联待办仍需单独完成。</p><label>补充进展（可选）<textarea maxLength={4000} value={progress} onChange={event => setProgress(event.target.value)} placeholder="只记录实际进展，不会自动标记完成" /></label><div className="focus-actions"><button type="button" onClick={() => void saveProgress()}>保存进展</button><button type="button" className="secondary" onClick={prepareNewFocus}>开始新专注</button></div></section>}
      </section>}
        {tab === 'routines' && <div className="focus-grid"><section className="panel focus-main"><div className="focus-section-head"><div><h2>每日重复任务</h2><p className="muted">只生成今天符合星期的独立待办，不追补缺席日期。</p></div><button type="button" className="secondary" disabled={routineBusy} onClick={() => void refill()}>检查并补齐今天</button></div><div className="focus-routine-list">{routines.length === 0 && <p className="muted">还没有重复规则。</p>}{routines.map(routine => <article key={routine.id} className="focus-routine"><div><strong>{routine.title}</strong><p>{routine.weekdays.map(day => weekdays[day - 1]).join('、')} · 默认 {routine.defaultDurationMinutes} 分钟 · {routine.enabled ? '已启用' : '已停用'}</p></div><div className="focus-actions"><button type="button" className="text-button"  onClick={() => { setRoutineEdit(routine); setRoutineInput({ title: routine.title, projectId: routine.projectId, weekdays: routine.weekdays, defaultDurationMinutes: routine.defaultDurationMinutes }) }}>编辑</button><button type="button" className="text-button" disabled={routineBusy} onClick={() => void toggle(routine)}>{routine.enabled ? '停用' : '启用'}</button></div></article>)}</div></section><section className="panel focus-side"><h2>{routineEdit ? '编辑重复规则' : '新建重复规则'}</h2><form className="focus-form" onSubmit={event => void submitRoutine(event)}><label>名称<input required maxLength={200} value={routineInput.title} onChange={event => setRoutineInput(value => ({ ...value, title: event.target.value }))} /></label><label>项目<select value={routineInput.projectId ?? ''} onChange={event => setRoutineInput(value => ({ ...value, projectId: event.target.value || null }))}><option value="">未归属项目</option>{projects.filter(project => project.status === 'ACTIVE' || project.id === routineInput.projectId).map(project => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select></label><fieldset><legend>重复星期</legend><div className="focus-weekdays">{weekdays.map((label, index) => <label key={label}><input type="checkbox" checked={routineInput.weekdays.includes(index + 1)} onChange={event => setRoutineInput(value => ({ ...value, weekdays: event.target.checked ? [...value.weekdays, index + 1].sort() : value.weekdays.filter(day => day !== index + 1) }))} />{label}</label>)}</div></fieldset><label>默认专注时长（分钟）<input type="number" required min={1} max={480} value={routineInput.defaultDurationMinutes} onChange={event => setRoutineInput(value => ({ ...value, defaultDurationMinutes: Number(event.target.value) }))} /></label><div className="focus-actions"><button type="submit" disabled={routineBusy}>保存规则</button>{routineEdit && <button type="button" className="secondary" onClick={() => { setRoutineEdit(null); setRoutineInput(emptyRoutine) }}>取消编辑</button>}</div></form></section></div>}
        {tab === 'today' && <section className="panel focus-today"><div className="focus-section-head"><div><h2>今日专注汇总</h2><p className="muted">{today?.date ?? '今天'} · 汇总已结束会话的净时长</p></div><button type="button" className="secondary" onClick={() => void reload()}>刷新</button></div><div className="focus-stats"><div><strong>{formatDuration(today?.focusMs ?? 0)}</strong><span>会话净时长</span></div><div><strong>{formatDuration(today?.breakMs ?? 0)}</strong><span>微休息</span></div><div><strong>{today?.sessionCount ?? 0}</strong><span>已结束会话</span></div></div><div className="focus-today-list">{today?.records.map(record => <article key={record.id}><strong>{record.content}</strong><span>会话净时长 {formatDuration(record.focusMs ?? 0)}{record.progress ? ` · 进展：${record.progress}` : ''}</span></article>)}{today?.records.length === 0 && <p className="muted">今天还没有已结束的专注会话。</p>}</div></section>}
    </div>
  </div>
}
