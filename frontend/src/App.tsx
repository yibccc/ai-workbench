import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchProjects, type Project } from './api/projects'
import { AppShell } from './components/layout/AppShell'
import { navigation, type PageId } from './components/layout/navigation'
import { RetainedView } from './components/RetainedView'
import { RecordsPage } from './features/records/RecordsPage'
import { TasksPanel, type TaskEditRequest } from './features/tasks/TasksPanel'
import { ReportsPage } from './features/reports/ReportsPage'
import { ProjectsPanel } from './features/projects/ProjectsPanel'
import { ToastProvider } from './components/ToastProvider'

const readPage = (): PageId => {
  const value = window.location.hash.slice(1)
  return navigation.some(item => item.id === value) ? value as PageId : 'records'
}
function App() {
  const [page, setPage] = useState<PageId>(readPage)
  const [projects, setProjects] = useState<Project[]>([])
  const [projectError, setProjectError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [taskEdit, setTaskEdit] = useState<TaskEditRequest | null>(null)
  const [dirtyReports, setDirtyReports] = useState(false)
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
    const change = () => setPage(readPage())
    window.addEventListener('hashchange', change)
    return () => window.removeEventListener('hashchange', change)
  }, [])
  useEffect(() => {
    document.title = `${navigation.find(item => item.id === page)!.label} · 工作台`
    if (firstNavigation.current) { firstNavigation.current = false; return }
    document.getElementById('main-content')?.focus({ preventScroll: true })
    document.querySelector('#main-content > .retained-view:not([hidden]) .workspace-scroll')?.scrollTo({ top: 0 })
    document.querySelectorAll('#main-content > .retained-view:not([hidden]) .reports-page > .retained-view:not([hidden]) :is(.report-scroll, .report-document, .source-rows)')
      .forEach(region => region.scrollTo({ top: 0 }))
  }, [page])
  const refreshContent = useCallback(async () => setRevision(value => value + 1), [])
  const editTask = useCallback((id: string) => {
    setTaskEdit({ id, request: Date.now() })
    window.location.hash = 'tasks'
    setPage('tasks')
  }, [])
  return <ToastProvider page={page}><AppShell page={page} hasDirtyReports={dirtyReports}>
    {projectError && <div className="notice error" role="alert"><span>项目列表读取失败：{projectError}</span><button className="text-button" type="button" onClick={() => void refreshProjects().catch((caught: unknown) => setProjectError(caught instanceof Error ? caught.message : '加载失败'))}>重新加载</button></div>}
    <RetainedView active={page === 'records'}><RecordsPage projects={projects} revision={revision} onDataChanged={refreshContent} onEditTask={editTask} /></RetainedView>
    <RetainedView active={page === 'tasks'}><TasksPanel projects={projects} refreshKey={revision} editRequest={taskEdit} onRecordsChanged={refreshContent} /></RetainedView>
    <RetainedView active={page === 'reports'}><ReportsPage onDirtyChange={setDirtyReports} /></RetainedView>
    <RetainedView active={page === 'projects'}><ProjectsPanel onProjectsChanged={async () => { await refreshProjects(); await refreshContent() }} /></RetainedView>
  </AppShell></ToastProvider>
}
export default App
