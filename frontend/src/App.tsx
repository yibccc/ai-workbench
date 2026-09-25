import { useCallback, useEffect, useRef, useState } from 'react'
import { getCurrentAccount, logout, signalActivity, type Account } from './api/auth'
import { currentRequestIdentity, setRequestIdentity, setUnauthorizedHandler } from './api/http'
import { closeRealtime } from './hooks/realtime'
import { fetchProjects, type Project } from './api/projects'
import { AppShell } from './components/layout/AppShell'
import { navigation, type PageId } from './components/layout/navigation'
import { RetainedView } from './components/RetainedView'
import { RecordsPage } from './features/records/RecordsPage'
import { TasksPanel, type TaskEditRequest } from './features/tasks/TasksPanel'
import { ReportsPage } from './features/reports/ReportsPage'
import { ProjectsPanel } from './features/projects/ProjectsPanel'
import { ToastProvider } from './components/ToastProvider'
import { useGlobalToast } from './components/toastContext'
import { LoginPage } from './features/auth/LoginPage'
import { PasswordDialog } from './features/auth/PasswordDialog'
import { UsersPage } from './features/auth/UsersPage'

type ViewId = PageId | 'users'
const readPage = (account: Account | null): ViewId => {
  const value = window.location.hash.slice(1)
  if (value === 'users' && account?.role === 'ADMIN') return 'users'
  return navigation.some(item => item.id === value) ? value as PageId : 'records'
}
const normalizedPage = (account: Account): ViewId => {
  const page = readPage(account)
  if (window.location.hash.slice(1) !== page) window.history.replaceState(null, '', `#${page}`)
  return page
}

function App() {
  const [account, setAccount] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [startupError, setStartupError] = useState<string | null>(null)
  const [page, setPage] = useState<ViewId>('records')
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
  if (!account) return <LoginPage onLogin={onLogin} />
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
  const firstNavigation = useRef(true)
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
    const change = () => setPage(normalizedPage(account))
    window.addEventListener('hashchange', change)
    return () => window.removeEventListener('hashchange', change)
  }, [account, setPage])
  useEffect(() => {
    document.title = `${page === 'users' ? '用户管理' : navigation.find(item => item.id === page)!.label} · 工作台`
    if (firstNavigation.current) { firstNavigation.current = false; return }
    document.getElementById('main-content')?.focus({ preventScroll: true })
    document.querySelector('#main-content > .retained-view:not([hidden]) .workspace-scroll')?.scrollTo({ top: 0 })
    document.querySelectorAll('#main-content > .retained-view:not([hidden]) .reports-page > .retained-view:not([hidden]) :is(.report-scroll, .report-document, .source-rows)')
      .forEach(region => region.scrollTo({ top: 0 }))
  }, [page])
  const refreshContent = useCallback(async () => setRevision(value => value + 1), [])
  const editTask = useCallback((id: string) => {
    setTaskEdit({ id, request: Date.now() }); window.location.hash = 'tasks'; setPage('tasks')
  }, [setPage])
  return <><AppShell page={page} hasDirtyReports={dirtyReports} account={account} onPassword={() => setPasswordOpen(true)} onLogout={onLogout}
    onUsers={() => { window.location.hash = 'users'; setPage('users') }}>
    {projectError && <div className="notice error" role="alert"><span>项目列表读取失败：{projectError}</span><button className="text-button" type="button" onClick={() => void refreshProjects().catch((caught: unknown) => setProjectError(caught instanceof Error ? caught.message : '加载失败'))}>重新加载</button></div>}
    <RetainedView active={page === 'records'}><RecordsPage projects={projects} revision={revision} onDataChanged={refreshContent} onEditTask={editTask} /></RetainedView>
    <RetainedView active={page === 'tasks'}><TasksPanel projects={projects} refreshKey={revision} editRequest={taskEdit} onRecordsChanged={refreshContent} /></RetainedView>
    <RetainedView active={page === 'reports'}><ReportsPage onDirtyChange={setDirtyReports} /></RetainedView>
    <RetainedView active={page === 'projects'}><ProjectsPanel onProjectsChanged={async () => { await refreshProjects(); await refreshContent() }} /></RetainedView>
    {page === 'users' && account.role === 'ADMIN' && <UsersPage currentId={account.id} onSelfRevoked={onSelfRevoked} />}
  </AppShell>
    {passwordOpen && <PasswordDialog onClose={() => setPasswordOpen(false)} onChanged={() => showToast('密码已修改，其他设备上的会话已失效', 'success')} />}
  </>
}
export default App
