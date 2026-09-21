import { type FormEvent } from 'react'
import { type Project } from '../../api/projects'
import { Pagination } from '../../components/Pagination'
import { useDialog } from '../../components/dialogContext'

export function ProjectsPanel({ items, name, search, page, size, totalPages, busy, loading,
  onName, onSearch, onPage, onSize, onSubmit, onRename, onArchive }: {
  items: Project[]; name: string; search: string; page: number; size: number; totalPages: number; busy: boolean; loading: boolean
  onName: (value: string) => void; onSearch: (value: string) => void
  onPage: (value: number) => void; onSize: (value: number) => void
  onSubmit: (event: FormEvent) => void; onRename: (id: string, name: string) => Promise<void>
  onArchive: (id: string) => Promise<void>
}) {
  const showDialog = useDialog()
  return <aside className="panel projects">
    <p className="kicker">PROJECTS</p><h2>项目</h2>
    <form className="project-form" onSubmit={onSubmit}>
      <input required maxLength={120} value={name} onChange={event => onName(event.target.value)} placeholder="新项目名称" />
      <button disabled={busy} type="submit">创建</button>
    </form>
    <div className="project-list">
      <input aria-label="搜索项目列表" value={search} onChange={event => onSearch(event.target.value)} placeholder="搜索项目" />
      {items.length === 0 ? <p className="muted">还没有匹配项目。</p> : items.map(project =>
        <div className={`project ${project.status === 'ARCHIVED' ? 'project-archived' : ''}`} key={project.id}>
          <div><strong>{project.name}</strong><span>{project.status === 'ACTIVE' ? '进行中' : '已归档'}</span></div>
          <div className="project-actions">
            <button className="text-button" type="button" onClick={() => void showDialog({ title: '修改项目名称', description: project.name, confirmLabel: '保存名称', input: { label: '项目名称', value: project.name, maxLength: 120 }, onConfirm: async next => { if (next !== project.name) await onRename(project.id, next) } })}>改名</button>
            {project.status === 'ACTIVE' && <button className="text-button danger" type="button" onClick={() => void showDialog({ title: '归档项目？', description: `归档“${project.name}”后，历史记录仍会保留。`, confirmLabel: '确认归档', danger: true, onConfirm: () => onArchive(project.id) })}>归档</button>}
          </div>
        </div>)}
    </div>
    <Pagination page={page} totalPages={totalPages} size={size} loading={loading} onPage={onPage} onSize={onSize} />
  </aside>
}
