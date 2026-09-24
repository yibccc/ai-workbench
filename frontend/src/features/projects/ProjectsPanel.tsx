import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { archiveProject, createProject, fetchProjectPage, renameProject } from '../../api/projects'
import { WORKSPACE_PAGE_SIZE } from '../../api/pagination'
import { Pagination } from '../../components/Pagination'
import { Icon } from '../../components/Icon'
import { useDialog } from '../../components/dialogContext'
import { usePagedList } from '../../hooks/usePagedList'
import { useToast } from '../../components/toastContext'

const ignoreListError = () => undefined

export function ProjectsPanel({ onProjectsChanged }: { onProjectsChanged: () => Promise<void> }) {
  const showDialog = useDialog()
  const { notify, toastRef } = useToast<HTMLDivElement>()
  const [name, setName] = useState('')
  const [search, setSearch] = useState('')
  const [queryText, setQueryText] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  useEffect(() => { const timer = window.setTimeout(() => { setQueryText(search.trim()); setPage(0) }, 250); return () => window.clearTimeout(timer) }, [search])
  const query = useCallback((signal: AbortSignal) => fetchProjectPage(page, WORKSPACE_PAGE_SIZE, queryText, includeArchived, signal), [page, queryText, includeArchived])
  const list = usePagedList(query, ignoreListError, setPage)
  const refresh = async () => {
    try { await Promise.all([list.refresh(), onProjectsChanged()]) }
    catch { notify('操作已保存，但项目列表刷新失败。请刷新页面，不要重复提交。', 'error') }
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current || !name.trim()) return
    pending.current = true; setBusy(true)
    try { await createProject(name.trim()); setName(''); setPage(0); notify('项目已创建', 'success'); await refresh() }
    catch (caught) { notify(caught instanceof Error ? caught.message : '创建失败', 'error') }
    finally { pending.current = false; setBusy(false) }
  }
  return <div ref={toastRef} className="page projects-page">
    <header className="page-header"><div><p className="page-eyebrow">让工作各有所属</p><h1>项目管理</h1><p className="page-description">统一管理项目名称与状态，历史记录始终保留。</p></div><span className="subtle-label">{list.loading ? '加载中…' : `${list.data?.totalElements ?? 0} 个匹配项目`}</span></header>
    <div className="project-controls" role="region" aria-label="项目操作" tabIndex={0}>
    <section className="panel project-create"><div><h2>开始一个新项目</h2><p className="field-hint">创建后，可在记录与待办中关联它。</p></div><form className="project-form" onSubmit={event => void submit(event)}><label className="sr-only" htmlFor="new-project-name">新项目名称</label><input id="new-project-name" required maxLength={120} disabled={busy} value={name} onChange={event => setName(event.target.value)} placeholder="例如：工作台体验优化" /><button disabled={busy || !name.trim()} type="submit"><Icon name="plus" size={16} />{busy ? '创建中…' : '创建项目'}</button></form></section>
    <div className="panel project-search"><div className="list-toolbar"><div className="search-field"><Icon name="search" size={17} /><input aria-label="搜索项目列表" value={search} onChange={event => setSearch(event.target.value)} placeholder="搜索项目名称…" /></div><label className="checkbox-label"><input type="checkbox" checked={includeArchived} onChange={event => { setIncludeArchived(event.target.checked); setPage(0) }} />包含已归档</label></div></div>
    </div>
    <div className="workspace-scroll" role="region" aria-label="项目列表内容" tabIndex={0}>
    <section className="panel projects" aria-label="项目列表">
      <div className="project-rows" role="region" aria-label="项目数据" tabIndex={0}>
      {list.error ? <div className="empty error-state" role="alert"><strong>暂时无法读取项目</strong><span>{list.error}</span><button className="secondary" type="button" onClick={() => void list.refresh()}>重新加载</button></div>
        : list.loading && !list.data ? <div className="loading-state" role="status"><span className="loading-dot" />正在读取项目…</div>
        : list.data?.items.length ? <div className="project-list">{list.data.items.map((project, index) => <article className={`project ${project.status === 'ARCHIVED' ? 'project-archived' : ''}`} key={project.id}><span className={`project-avatar tone-${index % 4}`}><Icon name="folder" size={21} /></span><div className="project-copy"><strong>{project.name}</strong><span className={`project-status ${project.status === 'ACTIVE' ? 'is-active' : ''}`}>{project.status === 'ACTIVE' ? '进行中' : '已归档'}</span></div><div className="project-actions"><button className="text-button" type="button" onClick={() => void showDialog({ title: '修改项目名称', description: '关联记录和待办中的项目名称也会随之更新。', confirmLabel: '保存名称', input: { label: '项目名称', value: project.name, maxLength: 120 }, onConfirm: async value => { if (value !== project.name) { await renameProject(project.id, value); notify('项目已改名', 'success'); void refresh() } } })}>改名</button>{project.status === 'ACTIVE' && <button className="text-button" type="button" onClick={() => void showDialog({ title: '归档项目？', description: `归档“${project.name}”后，历史记录仍会保留。新记录将不再默认提供这个项目。`, confirmLabel: '确认归档', danger: true, onConfirm: async () => { await archiveProject(project.id); notify('项目已归档', 'success'); void refresh() } })}><Icon name="archive" size={13} />归档</button>}</div></article>)}</div>
        : <div className="empty"><span className="empty-icon"><Icon name="folder" size={28} /></span><strong>{queryText ? '没有找到匹配的项目' : '还没有可显示的项目'}</strong><span>{queryText ? '试试其他关键词，或包含已归档项目。' : '在上方创建第一个项目，为工作建立一个归属。'}</span></div>}
      </div>
      {!list.error && <Pagination page={page} totalPages={list.data?.totalPages ?? 0} loading={list.loading} onPage={setPage} />}
    </section>
    </div>
  </div>
}
