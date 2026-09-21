import { type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  archiveProject } from './api/projects'
import { createProject, fetchProjects, fetchProjectPage, renameProject, type Project } from './api/projects'
import { createRecord, deleteRecord, fetchRecord, fetchRecordPage, updateRecord, type WorkRecord } from './api/records'
import { type CaptureInput } from './api/capture'
import { AiCapturePanel } from './features/capture/AiCapturePanel'
import { TasksPanel } from './features/tasks/TasksPanel'
import { DailyReportPanel } from './features/reports/DailyReportPanel'
import { WeeklyReportPanel } from './features/reports/WeeklyReportPanel'
import { CollapsibleSection } from './components/CollapsibleSection'
import { ProjectPicker } from './features/projects/ProjectPicker'
import { ProjectsPanel } from './features/projects/ProjectsPanel'
import { RecordsList } from './features/records/RecordsList'
import { WORKBENCH_TIME_ZONE, localDate, localDateTime, timeForDate, toInstant } from './utils/date'
import { usePagedList } from './hooks/usePagedList'
import { useDialog } from './components/dialogContext'

function App() {
  const showDialog = useDialog()
  const [projects, setProjects] = useState<Project[]>([])
  const [projectSearch, setProjectSearch] = useState('')
  const [projectPage, setProjectPage] = useState(0)
  const [projectSize, setProjectSize] = useState(20)
  const [recordPage, setRecordPage] = useState(0)
  const [recordSize, setRecordSize] = useState(20)
  const [selectedDate, setSelectedDate] = useState(localDate)
  const [dailyDate, setDailyDate] = useState(localDate)
  const [weeklyDate, setWeeklyDate] = useState(localDate)
  const [projectName, setProjectName] = useState('')
  const [content, setContent] = useState('')
  const [projectId, setProjectId] = useState('')
  const [occurredAt, setOccurredAt] = useState(localDateTime)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [captureRevision, setCaptureRevision] = useState(0)
  const [dailyReportDirty, setDailyReportDirty] = useState(false)
  const [weeklyReportDirty, setWeeklyReportDirty] = useState(false)
  const [captureSummary, setCaptureSummary] = useState<string>()
  const [taskSummary, setTaskSummary] = useState('0 项')
  const [dailySummary, setDailySummary] = useState<string | undefined>('0 个版本')
  const [weeklySummary, setWeeklySummary] = useState<string | undefined>('0 个版本')
  const changeSelectedDate = (nextDate: string) => {
    if (nextDate === selectedDate) return true
    setSelectedDate(nextDate)
    setRecordPage(0)
    if (!editingId) setOccurredAt(timeForDate(nextDate))
    return true
  }

  const loadProjects = useCallback(async () => setProjects(await fetchProjects(true)), [])
  const queryProjects = useCallback((signal: AbortSignal) =>
    fetchProjectPage(projectPage, projectSize, projectSearch, false, signal), [projectPage, projectSearch, projectSize])
  const queryRecords = useCallback((signal: AbortSignal) =>
    fetchRecordPage(selectedDate, recordPage, recordSize, signal), [selectedDate, recordPage, recordSize])
  const projectList = usePagedList(queryProjects, setError, setProjectPage)
  const recordList = usePagedList(queryRecords, setError, setRecordPage)
  const loadProjectPage = projectList.refresh
  const loadRecords = recordList.refresh
  const projectItems = projectList.data?.items ?? []
  const projectTotalPages = projectList.data?.totalPages ?? 0
  const records = recordList.data?.items ?? []
  const recordTotalPages = recordList.data?.totalPages ?? 0
  const recordTotal = recordList.data?.totalElements ?? 0

  useEffect(() => {
    const controller = new AbortController()
    void fetchProjects(true, controller.signal)
      .then(setProjects)
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof Error ? caught.message : '无法加载工作台')
      })
    return () => controller.abort()
  }, [])

  const run = async (action: () => Promise<void>, success: string, propagate = false) => {
    setBusy(true); setError(null); setMessage(null)
    try {
      await action()
      setMessage(success)
    } catch (caught) {
      if (propagate) throw caught
      setError(caught instanceof Error ? caught.message : '操作失败')
    } finally {
      setBusy(false)
    }
  }

  const submitProject = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      await createProject(projectName)
      setProjectName('')
      setProjectPage(0); await loadProjects(); await loadProjectPage()
    }, '项目已创建')
  }

  const submitRecord = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const input = { projectId: projectId || null, content, occurredAt: toInstant(occurredAt) }
      if (editingId) await updateRecord(editingId, input)
      else await createRecord(input)
      setContent(''); setProjectId(''); setOccurredAt(timeForDate(selectedDate)); setEditingId(null)
      setRecordPage(0); await loadRecords()
    }, editingId ? '工作记录已更新' : '工作记录已保存')
  }

  const beginEdit = (record: WorkRecord) => {
    setEditingId(record.id)
    setContent(record.content)
    setProjectId(record.project?.id ?? '')
    setOccurredAt(localDateTime(record.occurredAt))
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const refreshProjectRelatedViews = async () => {
    await Promise.all([loadProjects(), loadRecords()])
    await loadProjectPage()
  }

  return (
    <main className="shell" data-testid="workbench">
      <header className="hero">
        <div>
          <p className="eyebrow">LOCAL AI WORKBENCH</p>
          <h1>今日工作台</h1>
          <p className="lede">快速记录成果与安排，按日期整理工作，并生成可核对来源的日报和周报。</p>
        </div>
        <p className={`weekday weekday-${new Intl.DateTimeFormat('en-US', { timeZone: WORKBENCH_TIME_ZONE, weekday: 'short' }).format(new Date()).toLowerCase()}`}>今天是{new Intl.DateTimeFormat('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, weekday: 'long' }).format(new Date())}</p>
      </header>

      {(error || message) && <p className={error ? 'notice error' : 'notice success'} role={error ? 'alert' : 'status'}>{error ?? message}</p>}

      <section className="workspace">
        <div className="main-column">
          <CollapsibleSection id="weekly" title="自然周汇总" summary={weeklyReportDirty ? '有未保存修改' : weeklySummary} defaultOpen={false}><WeeklyReportPanel date={weeklyDate} onDateChange={async (date) => {
            if (!weeklyReportDirty || await showDialog({ title: '放弃未保存修改？', description: '周报还有未保存修改，切换日期会放弃这些内容。', confirmLabel: '放弃修改', danger: true })) setWeeklyDate(date)
          }} onDirtyChange={setWeeklyReportDirty} onSummaryChange={setWeeklySummary} /></CollapsibleSection>
          <CollapsibleSection id="daily" title="当天汇总" summary={dailyReportDirty ? '有未保存修改' : dailySummary} defaultOpen={false}><DailyReportPanel date={dailyDate} onDateChange={async (date) => {
            if (!dailyReportDirty || await showDialog({ title: '放弃未保存修改？', description: '日报正文尚未保存，切换日期会放弃这些内容。', confirmLabel: '放弃修改', danger: true })) setDailyDate(date)
          }} onDirtyChange={setDailyReportDirty} onSummaryChange={setDailySummary} /></CollapsibleSection>
          <CollapsibleSection id="capture" title="一句话记录与安排" summary={captureSummary} defaultOpen><AiCapturePanel onSummaryChange={setCaptureSummary} onEditRecord={(id) => {
            window.dispatchEvent(new CustomEvent('ai-workbench:open-panel', { detail: 'record-composer' }))
            void fetchRecord(id).then(beginEdit)
          }} onEditTask={(id) => {
            window.dispatchEvent(new CustomEvent('ai-workbench:open-panel', { detail: 'tasks' }))
            window.dispatchEvent(new CustomEvent('ai-workbench:edit-task', { detail: id }))
          }} onGenerated={async (result: CaptureInput) => {
            const firstRecord = result.records[0]
            const generatedDate = firstRecord ? localDate(new Date(firstRecord.occurredAt)) : selectedDate
            if (firstRecord) changeSelectedDate(generatedDate)
            setRecordPage(0)
            if (recordPage === 0 && generatedDate === selectedDate) await loadRecords()
            setCaptureRevision((value) => value + 1)
          }} /></CollapsibleSection>
          <CollapsibleSection id="record-composer" title="手工记录" defaultOpen><article className="panel composer" data-testid="record-composer">
            <div className="section-heading">
              <div><p className="kicker">WORK LOG</p><h2>{editingId ? '编辑工作记录' : '记一笔工作'}</h2></div>
              <input aria-label="查看日期" type="date" value={selectedDate} onChange={(event) => {
                changeSelectedDate(event.target.value)
              }} />
            </div>
            <form onSubmit={submitRecord}>
              <label>工作内容<textarea data-testid="record-content" required maxLength={4000} rows={4} value={content} onChange={(event) => setContent(event.target.value)} onKeyDown={(event) => { if (event.ctrlKey && event.key === 'Enter') event.currentTarget.form?.requestSubmit() }} placeholder="完成了什么、解决了什么问题？（Ctrl+Enter 提交）" /></label>
              <div className="form-row">
                <label>所属项目<ProjectPicker projects={projects} value={projectId} onChange={setProjectId} emptyLabel="未归属项目" currentId={editingId ? projectId : null} /></label>
                <label>发生时间<input required type="datetime-local" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} /></label>
              </div>
              <div className="actions">
                <button data-testid="record-submit" disabled={busy} type="submit">{editingId ? '保存修改' : '保存记录'}</button>
                {editingId && <button className="secondary" type="button" onClick={() => { setEditingId(null); setContent(''); setProjectId(''); setOccurredAt(timeForDate(selectedDate)) }}>取消编辑</button>}
              </div>
            </form>
          </article></CollapsibleSection>

          <CollapsibleSection id="tasks" title="待办" summary={taskSummary} defaultOpen><TasksPanel key={captureRevision} projects={projects} onSummaryChange={setTaskSummary} onRecordsChanged={loadRecords} /></CollapsibleSection>

          <CollapsibleSection id="records" title="工作记录" summary={`${recordTotal} 条`} defaultOpen>
            <RecordsList selectedDate={selectedDate} records={records} recordTotal={recordTotal}
              recordPage={recordPage} recordSize={recordSize} recordTotalPages={recordTotalPages} loading={recordList.loading}
              setRecordPage={setRecordPage} setRecordSize={setRecordSize} beginEdit={beginEdit}
              onDelete={id => run(async () => { await deleteRecord(id); void loadRecords() }, '工作记录已删除', true)} />
          </CollapsibleSection>
        </div>

        <ProjectsPanel items={projectItems} name={projectName} search={projectSearch}
          page={projectPage} size={projectSize} totalPages={projectTotalPages} busy={busy} loading={projectList.loading}
          onName={setProjectName} onSearch={value => { setProjectSearch(value); setProjectPage(0) }}
          onPage={setProjectPage} onSize={value => { setProjectSize(value); setProjectPage(0) }} onSubmit={submitProject}
          onRename={(id, name) => run(async () => { await renameProject(id, name); void refreshProjectRelatedViews().catch(() => setError('项目已改名，但列表刷新失败，请刷新页面')) }, '项目已改名', true)}
          onArchive={id => run(async () => { await archiveProject(id); void refreshProjectRelatedViews().catch(() => setError('项目已归档，但列表刷新失败，请刷新页面'))
            if (projectId === id && !editingId) setProjectId('') }, '项目已归档', true)} />
      </section>
    </main>
  )
}

export default App
