import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentAccount, logout, signalActivity, type Account } from './api/auth'
import { currentRequestIdentity, setRequestIdentity, setUnauthorizedHandler } from './api/http'
import { closeRealtime } from './hooks/realtime'
import { fetchProjects, type Project } from './api/projects'
import { AppShell } from './components/layout/AppShell'
import { isCommunityView, parseHash, routeTitle, type ViewId } from './components/layout/routes'
import { CommunityWorkspace } from './features/community/CommunityWorkspace'
import type { NavigationGuard } from './features/publishing/PublishingEditor'
import { RetainedView } from './components/RetainedView'
import { RecordsPage } from './features/records/RecordsPage'
import { TasksPanel, type TaskEditRequest } from './features/tasks/TasksPanel'
import type { TaskItem } from './api/tasks'
import { FocusPage, type FocusDraft } from './features/focus/FocusPage'
import { formatDuration, projectedBreakMs, projectedFocusMs, useFocusController } from './features/focus/useFocusController'
import { fillToday } from './api/focus'
import { ReportsPage } from './features/reports/ReportsPage'
import { ProjectsPanel } from './features/projects/ProjectsPanel'
import { ToastProvider } from './components/ToastProvider'
import { useGlobalToast } from './components/toastContext'
import { LoginPage } from './features/auth/LoginPage'
import { PasswordDialog } from './features/auth/PasswordDialog'
import { UsersPage } from './features/auth/UsersPage'

const readPage = (account: Account | null): ViewId => {
  const value = parseHash(window.location.hash)
  return value === 'users' && account && account.role !== 'ADMIN' ? 'unavailable' : value
}
const normalizedPage = (account: Account): ViewId => {
  const page = readPage(account)
  if (!window.location.hash) window.history.replaceState(null, '', `#${page}`)
  return page
}

function App() {
  const [account, setAccount] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [startupError, setStartupError] = useState<string | null>(null)
  const [page, setPage] = useState<ViewId>(() => readPage(null))
  const started = useRef(false)
  const load = useCallback(async () => {
    setLoading(true); setStartupError(null)
    try {
      const current = await getCurrentAccount()
      setRequestIdentity(current.id); setAccount(current); setPage(normalizedPage(current))
    } catch (caught) {
      if ((caught as { status?: number }).status === 401) { setRequestIdentity(null); setAccount(null) }
      else setStartupError(caught instanceof Error ? caught.message : '无法连接服务')
    } finally { setLoading(false) }
  }, [])
  useEffect(() => { if (!started.current) { started.current = true; void load() } }, [load])
  return <ToastProvider page={account ? page : 'login'}>
    {loading ? <main className="login-page"><p role="status">正在加载工作台…</p></main> : startupError
      ? <main className="login-page"><div className="login-card"><h1>连接失败</h1><p role="alert">{startupError}</p><button onClick={() => void load()}>重试</button></div></main>
      : <AuthenticatedApp account={account} page={page} setPage={setPage} setAccount={setAccount} />}
  </ToastProvider>
}

function AuthenticatedApp({ account, page, setPage, setAccount }: {
  account: Account | null; page: ViewId; setPage: (page: ViewId) => void; setAccount: (account: Account | null) => void
}) {
  const showToast = useGlobalToast()
  const lastActivity = useRef(0)
  const signingOut = useRef(false)
  const leaveAuthenticatedView = useCallback((message: string, kind: 'success' | 'info' | 'error') => {
    closeRealtime(); setRequestIdentity(null); setAccount(null)
    showToast(message, kind, 'login')
  }, [setAccount, showToast])
  const lost = useCallback(() => {
    if (!account || signingOut.current) return
    leaveAuthenticatedView('会话已失效，请重新登录', 'error')
  }, [account, leaveAuthenticatedView])
  useEffect(() => { setUnauthorizedHandler(lost); return () => setUnauthorizedHandler(null) }, [lost])
  const onLogin = (next: Account) => {
    closeRealtime(); setRequestIdentity(next.id); setAccount(next); setPage(normalizedPage(next)); lastActivity.current = 0
  }
  const onLogout = async () => {
    if (signingOut.current) return
    signingOut.current = true
    try {
      await logout()
      leaveAuthenticatedView('已退出登录', 'success')
    } catch (caught) {
      if ((caught as { status?: number }).status === 401) {
        leaveAuthenticatedView('会话已失效，请重新登录', 'error')
      } else showToast(caught instanceof Error ? caught.message : '退出失败，请重试', 'error')
    } finally {
      signingOut.current = false
    }
  }
  const onInteraction = useCallback(() => {
    if (!account || currentRequestIdentity() !== account.id || Date.now() - lastActivity.current < 60_000) return
    lastActivity.current = Date.now()
    void signalActivity().catch(() => { lastActivity.current = 0 })
  }, [account])
  useEffect(() => {
    if (!account) return
    const belongsToWorkbench = (target: EventTarget | null) => target instanceof Element
      && !!target.closest('.app-shell, .workbench-dialog') && !target.closest('[data-no-activity]')
    const pointer = (event: PointerEvent) => {
      if (belongsToWorkbench(event.target) && event.target instanceof Element
          && event.target.closest('button, a, input, textarea, select, label, summary, [role="button"], [contenteditable="true"]')) onInteraction()
    }
    const input = (event: Event) => { if (belongsToWorkbench(event.target)) onInteraction() }
    const submit = (event: Event) => { if (belongsToWorkbench(event.target)) onInteraction() }
    const key = (event: KeyboardEvent) => {
      if (!belongsToWorkbench(event.target) || !(event.target instanceof Element)) return
      const editsText = (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement)
        && (event.key.length === 1 || event.key === 'Backspace' || event.key === 'Delete')
      const activatesControl = (event.key === 'Enter' || event.key === ' ')
        && !!event.target.closest('button, a, [role="button"]')
      if (editsText || activatesControl) onInteraction()
    }
    window.addEventListener('pointerdown', pointer)
    window.addEventListener('input', input)
    window.addEventListener('submit', submit)
    window.addEventListener('keydown', key)
    return () => {
      window.removeEventListener('pointerdown', pointer)
      window.removeEventListener('input', input)
      window.removeEventListener('submit', submit)
      window.removeEventListener('keydown', key)
    }
  }, [account, onInteraction])
  if (!account) return isCommunityView(readPage(null)) ? <AppShell page={readPage(null)} hasDirtyReports={false} account={null} onPassword={() => undefined} onLogout={() => undefined} onUsers={() => undefined}><div className="page-main"><div className="login-wrap"><LoginPage gated onLogin={onLogin} /></div></div></AppShell> : <LoginPage onLogin={onLogin} />
  return <Workspace key={account.id} account={account} page={page} setPage={setPage} onLogout={() => void onLogout()}
    onSelfRevoked={() => leaveAuthenticatedView('账号权限或密码已变更，请重新登录', 'info')} showToast={showToast} />
}

function Workspace({ account, page, setPage, onLogout, onSelfRevoked, showToast }: {
  account: Account; page: ViewId; setPage: (page: ViewId) => void; onLogout: () => void; onSelfRevoked: () => void; showToast: ReturnType<typeof useGlobalToast>
}) {
  const [projects, setProjects] = useState<Project[]>([])
  const [projectError, setProjectError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [taskEdit, setTaskEdit] = useState<TaskEditRequest | null>(null)
  const [dirtyReports, setDirtyReports] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const [focusDraft, setFocusDraft] = useState<FocusDraft | null>(null)
  const [focusFillError, setFocusFillError] = useState<string | null>(null)
  const firstNavigation = useRef(true)
  const communityGuard = useRef<NavigationGuard | null>(null)
  const routeRef = useRef(page)
  useEffect(() => { routeRef.current = page }, [page])
  const changingRoute = useRef(false)
  const registerGuard = useCallback((guard: NavigationGuard | null) => { communityGuard.current = guard }, [])
  const navigate = useCallback((next: ViewId) => { window.location.hash = next }, [])
  const refreshProjects = useCallback(async () => {
    const items = await fetchProjects(true)
    setProjects(items); setProjectError(null)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    void fetchProjects(true, controller.signal).then(items => { if (!controller.signal.aborted) setProjects(items) }).catch((caught: unknown) => {
      if (!controller.signal.aborted) setProjectError(caught instanceof Error ? caught.message : '项目加载失败')
    })
    return () => controller.abort()
  }, [])
  useEffect(() => {
    const change = async () => {
      const next = normalizedPage(account)
      if (changingRoute.current) { window.history.replaceState(null, '', `#${routeRef.current}`); return }
      if (next === routeRef.current) return
      const previous = routeRef.current
      if (communityGuard.current) {
        window.history.replaceState(null, '', `#${previous}`)
        changingRoute.current = true
        const accepted = await communityGuard.current(next)
        changingRoute.current = false
        if (!accepted || currentRequestIdentity() !== account.id) return
        window.history.replaceState(null, '', `#${next}`)
      }
      routeRef.current = next; setPage(next)
    }
    window.addEventListener('hashchange', change)
    return () => window.removeEventListener('hashchange', change)
  }, [account, setPage])
  useEffect(() => {
    document.title = `${routeTitle(page)} · 工作台`
    if (firstNavigation.current) { firstNavigation.current = false; return }
    document.getElementById('main-content')?.focus({ preventScroll: true })
    if (isCommunityView(page)) document.getElementById('main-content')?.scrollTo({ top: 0 })
    document.querySelector('#main-content > .retained-view:not([hidden]) .workspace-scroll')?.scrollTo({ top: 0 })
    document.querySelectorAll('#main-content > .retained-view:not([hidden]) .reports-page > .retained-view:not([hidden]) :is(.report-scroll, .report-document, .source-rows)')
      .forEach(region => region.scrollTo({ top: 0 }))
  }, [page])
  const refreshContent = useCallback(async () => setRevision(value => value + 1), [])
  const onFocusSettled = useCallback(() => { void refreshContent() }, [refreshContent])
  const focus = useFocusController(account.id, onFocusSettled)
  const prepareFocus = useCallback(async () => {
    try {
      const result = await fillToday()
      setFocusFillError(null)
      if (result.blocked.length) showToast(`有 ${result.blocked.length} 条重复规则未生成今日待办：${result.blocked.map(item => item.reason).join('；')}`, 'info')
      void refreshContent()
    } catch (caught) { setFocusFillError(caught instanceof Error ? caught.message : '今日重复任务检查失败') }
  }, [refreshContent, showToast])
  useEffect(() => { void Promise.resolve().then(prepareFocus) }, [prepareFocus])
  const focusTask = useCallback((task: TaskItem) => {
    setFocusDraft({ taskId: task.id, title: task.title, projectId: task.project?.id ?? null,
      targetMinutes: task.defaultFocusDurationMinutes ?? 25, nonce: Date.now() })
    window.location.hash = 'focus'; setPage('focus')
  }, [setPage])
  const editTask = useCallback((id: string) => {
    setTaskEdit({ id, request: Date.now() }); window.location.hash = 'tasks'; setPage('tasks')
  }, [setPage])
  const currentFocus = focus.session?.phase === 'ENDED' ? null : focus.session
  const focusStatus = currentFocus ? `${currentFocus.title} · ${currentFocus.phase === 'RUNNING' ? formatDuration(Math.max(0, currentFocus.targetMs - projectedFocusMs(currentFocus, focus.now))) : currentFocus.phase === 'MICRO_BREAK' ? '微休息' : '已暂停'}` : null
  const focusToggle = currentFocus?.phase === 'RUNNING' ? { label: '暂停', disabled: focus.busy || focus.unverified, onClick: () => { void focus.transition('PAUSE') } }
    : currentFocus?.phase === 'PAUSED' ? { label: '继续', disabled: focus.busy || focus.unverified, onClick: () => { void focus.transition('RESUME') } } : null
  const breakOverlayVisible = currentFocus?.phase === 'MICRO_BREAK' && !focus.unverified
  const soundAlert = focus.soundError && <div className="notice error focus-sound-alert" role="alert" data-testid="focus-sound-alert">
    <span>{focus.soundError}</span><div className="focus-sound-alert-actions"><a href="#focus">返回专注</a><button type="button" className="text-button" onClick={() => void focus.enableSound()}>{focus.soundAction}</button></div>
  </div>
  return <><AppShell page={page} hasDirtyReports={dirtyReports} focusStatus={focusStatus} focusToggle={focusToggle} account={account} onPassword={() => setPasswordOpen(true)} onLogout={onLogout}
    onUsers={() => { window.location.hash = 'users' }}>
    {projectError && <div className="notice error" role="alert"><span>项目列表读取失败：{projectError}</span><button className="text-button" type="button" onClick={() => void refreshProjects().catch((caught: unknown) => setProjectError(caught instanceof Error ? caught.message : '加载失败'))}>重新加载</button></div>}
    {focusFillError && <div className="notice error" role="alert"><span>今日重复任务检查失败：{focusFillError}</span><button className="text-button" type="button" onClick={() => void fillToday().then(result => { setFocusFillError(null); if (result.blocked.length) showToast(`有 ${result.blocked.length} 条规则未生成：${result.blocked.map(item => item.reason).join('；')}`, 'info'); void refreshContent() }).catch((caught: unknown) => setFocusFillError(caught instanceof Error ? caught.message : '重试失败'))}>重试</button></div>}
    {page !== 'focus' && !breakOverlayVisible && !focus.alarmActive && soundAlert}
    <RetainedView active={page === 'records'}><RecordsPage projects={projects} revision={revision} onDataChanged={refreshContent} onEditTask={editTask} /></RetainedView>
    <RetainedView active={page === 'tasks'}><TasksPanel projects={projects} refreshKey={revision} editRequest={taskEdit} onRecordsChanged={refreshContent} onFocusTask={focusTask} /></RetainedView>
    <RetainedView active={page === 'focus'}><FocusPage projects={projects} draft={focusDraft} control={focus} revision={revision} onTasksChanged={() => { void refreshContent() }} /></RetainedView>
    <RetainedView active={page === 'reports'}><ReportsPage onDirtyChange={setDirtyReports} /></RetainedView>
    <RetainedView active={page === 'projects'}><ProjectsPanel onProjectsChanged={async () => { await refreshProjects(); await refreshContent() }} /></RetainedView>
    <RetainedView active={isCommunityView(page)}><CommunityWorkspace account={account} page={page} navigate={navigate} registerGuard={registerGuard} /></RetainedView>
    {page === 'users' && account.role === 'ADMIN' && <UsersPage currentId={account.id} onSelfRevoked={onSelfRevoked} />}
  </AppShell>
    {focus.alarmActive && <div className="focus-completion-alarm" role="alert" data-testid="focus-completion-alarm">
      <div><strong>专注已达标并保存</strong><p>{focus.soundError ?? '达标铃声正在响起，请点击“结束”停止。'}</p></div>
      <div className="focus-completion-actions">{focus.soundError && <button type="button" className="secondary" onClick={() => void focus.enableSound()}>{focus.soundAction}</button>}<button type="button" onClick={focus.stopAlarm}>结束</button></div>
    </div>}
    {focus.reminderNotice && currentFocus?.phase !== 'MICRO_BREAK' && <div className="focus-reminder-notice" role="status">{focus.reminderNotice}</div>}
    {breakOverlayVisible && currentFocus && <div className="focus-break-overlay" role="dialog" aria-label="微休息引导" aria-modal="false"><div className="focus-break-card"><p className="page-eyebrow">微休息</p><h2>闭眼放松 15 秒</h2><p className="focus-break-time">{formatDuration(Math.max(0, currentFocus.breakRemainingMs - (projectedBreakMs(currentFocus, focus.now) - currentFocus.breakMs)))}</p><p className="muted">这是休息引导，不检测您的状态。可随时跳过或关闭本段后续提醒。</p>{soundAlert}<div className="focus-actions"><button type="button" disabled={focus.busy} onClick={() => void focus.transition('SKIP_BREAK')}>跳过本次</button><button type="button" className="secondary" disabled={focus.busy} onClick={() => void focus.transition('DISMISS_REMINDERS')}>关闭本段提醒</button><a className="secondary focus-break-link" href="#focus">查看专注</a></div></div></div>}
    {passwordOpen && <PasswordDialog onClose={() => setPasswordOpen(false)} onChanged={() => showToast('密码已修改，其他设备上的会话已失效', 'success')} />}
  </>
}
export default App
