import { type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  archiveProject, createProject, createRecord, deleteRecord, fetchProjects, fetchRecords,
  fetchWorkbenchStatus, renameProject, updateRecord,
  type Project, type WorkRecord, type WorkbenchStatus,
} from './api'
import { TasksPanel } from './TasksPanel'

const WORKBENCH_TIME_ZONE = 'Asia/Shanghai'
const workbenchParts = (date = new Date()) => Object.fromEntries(
  new Intl.DateTimeFormat('en-CA', {
    timeZone: WORKBENCH_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date).map(({ type, value }) => [type, value]),
)
const localDate = (date = new Date()) => {
  const parts = workbenchParts(date)
  return `${parts.year}-${parts.month}-${parts.day}`
}
const localDateTime = (iso?: string) => {
  const parts = workbenchParts(iso ? new Date(iso) : new Date())
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}
const timeForDate = (date: string) => `${date}T${localDateTime().slice(11)}`
const toInstant = (dateTime: string) => new Date(`${dateTime}:00+08:00`).toISOString()

function App() {
  const [status, setStatus] = useState<WorkbenchStatus | null>(null)
  const [projects, setProjects] = useState<Project[]>([])
  const [records, setRecords] = useState<WorkRecord[]>([])
  const [selectedDate, setSelectedDate] = useState(localDate)
  const [projectName, setProjectName] = useState('')
  const [content, setContent] = useState('')
  const [projectId, setProjectId] = useState('')
  const [occurredAt, setOccurredAt] = useState(localDateTime)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadProjects = useCallback(async () => setProjects(await fetchProjects(true)), [])
  const loadRecords = useCallback(async (date: string) => setRecords(await fetchRecords(date)), [])

  useEffect(() => {
    const controller = new AbortController()
    void Promise.all([fetchWorkbenchStatus(controller.signal), fetchProjects(true)])
      .then(([nextStatus, nextProjects]) => {
        setStatus(nextStatus)
        setProjects(nextProjects)
      })
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof Error ? caught.message : '无法加载工作台')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    let active = true
    void fetchRecords(selectedDate)
      .then((nextRecords) => { if (active) setRecords(nextRecords) })
      .catch((caught: unknown) => {
        if (active) setError(caught instanceof Error ? caught.message : '无法读取工作记录')
      })
    return () => { active = false }
  }, [selectedDate])

  const run = async (action: () => Promise<void>, success: string) => {
    setBusy(true); setError(null); setMessage(null)
    try {
      await action()
      setMessage(success)
    } catch (caught) {
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
      await loadProjects()
    }, '项目已创建')
  }

  const submitRecord = (event: FormEvent) => {
    event.preventDefault()
    void run(async () => {
      const input = { projectId: projectId || null, content, occurredAt: toInstant(occurredAt) }
      if (editingId) await updateRecord(editingId, input)
      else await createRecord(input)
      setContent(''); setProjectId(''); setOccurredAt(timeForDate(selectedDate)); setEditingId(null)
      await loadRecords(selectedDate)
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
    await Promise.all([loadProjects(), loadRecords(selectedDate)])
  }

  return (
    <main className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">LOCAL AI WORKBENCH</p>
          <h1>今日工作台</h1>
          <p className="lede">记录真实发生的工作，也可以切换日期补记历史。所有数据保存在本机 PostgreSQL。</p>
        </div>
        <div className="health" aria-label="运行状态">
          {status ? Object.entries(status.components).map(([name, component]) => (
            <span className={`health-item health-${component.status.toLowerCase()}`} key={name} title={component.detail}>
              {name} · {component.status}
            </span>
          )) : <span>正在连接本机服务…</span>}
        </div>
      </header>

      {(error || message) && <p className={error ? 'notice error' : 'notice success'} role={error ? 'alert' : 'status'}>{error ?? message}</p>}

      <section className="workspace">
        <div className="main-column">
          <article className="panel composer">
            <div className="section-heading">
              <div><p className="kicker">WORK LOG</p><h2>{editingId ? '编辑工作记录' : '记一笔工作'}</h2></div>
              <input aria-label="查看日期" type="date" value={selectedDate} onChange={(event) => {
                const nextDate = event.target.value
                setSelectedDate(nextDate)
                if (!editingId) setOccurredAt(timeForDate(nextDate))
              }} />
            </div>
            <form onSubmit={submitRecord}>
              <label>工作内容<textarea required maxLength={4000} rows={4} value={content} onChange={(event) => setContent(event.target.value)} placeholder="完成了什么、解决了什么问题？" /></label>
              <div className="form-row">
                <label>所属项目<select value={projectId} onChange={(event) => setProjectId(event.target.value)}><option value="">未归属项目</option>{projects.map((project) => <option disabled={project.status === 'ARCHIVED' && project.id !== projectId} key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}</select></label>
                <label>发生时间<input required type="datetime-local" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} /></label>
              </div>
              <div className="actions">
                <button disabled={busy} type="submit">{editingId ? '保存修改' : '保存记录'}</button>
                {editingId && <button className="secondary" type="button" onClick={() => { setEditingId(null); setContent(''); setProjectId(''); setOccurredAt(timeForDate(selectedDate)) }}>取消编辑</button>}
              </div>
            </form>
          </article>

          <TasksPanel projects={projects} />

          <section className="records" aria-labelledby="records-title">
            <div className="section-heading"><div><p className="kicker">TIMELINE</p><h2 id="records-title">{selectedDate} 的记录</h2></div><span>{records.length} 条</span></div>
            {records.length === 0 ? (
              <div className="empty"><strong>这一天还没有记录</strong><span>在上方写下第一条，或切换日期补记历史工作。</span></div>
            ) : records.map((record) => (
              <article className="record" key={record.id}>
                <time>{new Date(record.occurredAt).toLocaleTimeString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, hour: '2-digit', minute: '2-digit' })}</time>
                <div className="record-body">
                  <div className="record-meta">
                    {record.project ? <span className={record.project.status === 'ARCHIVED' ? 'archived' : ''}>{record.project.name}{record.project.status === 'ARCHIVED' ? '（已归档）' : ''}</span> : <span>未归属项目</span>}
                    <span>录入于 {new Date(record.createdAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE })}</span>
                  </div>
                  <p>{record.content}</p>
                </div>
                <div className="record-actions">
                  <button className="text-button" type="button" onClick={() => beginEdit(record)}>编辑</button>
                  <button className="text-button danger" type="button" onClick={() => {
                    if (window.confirm('确定删除这条工作记录吗？')) void run(async () => {
                      await deleteRecord(record.id); await loadRecords(selectedDate)
                    }, '工作记录已删除')
                  }}>删除</button>
                </div>
              </article>
            ))}
          </section>
        </div>

        <aside className="panel projects">
          <p className="kicker">PROJECTS</p><h2>项目</h2>
          <form className="project-form" onSubmit={submitProject}>
            <input required maxLength={120} value={projectName} onChange={(event) => setProjectName(event.target.value)} placeholder="新项目名称" />
            <button disabled={busy} type="submit">创建</button>
          </form>
          <div className="project-list">
            {projects.length === 0 ? <p className="muted">还没有项目，先创建一个吧。</p> : projects.map((project) => (
              <div className={`project ${project.status === 'ARCHIVED' ? 'project-archived' : ''}`} key={project.id}>
                <div><strong>{project.name}</strong><span>{project.status === 'ACTIVE' ? '进行中' : '已归档'}</span></div>
                <div className="project-actions">
                  <button className="text-button" type="button" onClick={() => {
                    const name = window.prompt('新的项目名称', project.name)
                    if (name?.trim() && name.trim() !== project.name) void run(async () => {
                      await renameProject(project.id, name); await refreshProjectRelatedViews()
                    }, '项目已改名')
                  }}>改名</button>
                  {project.status === 'ACTIVE' && <button className="text-button danger" type="button" onClick={() => {
                    if (window.confirm(`归档“${project.name}”？历史记录仍会保留。`)) void run(async () => {
                      await archiveProject(project.id); await refreshProjectRelatedViews()
                      if (projectId === project.id && !editingId) setProjectId('')
                    }, '项目已归档')
                  }}>归档</button>}
                </div>
              </div>
            ))}
          </div>
        </aside>
      </section>
    </main>
  )
}

export default App
