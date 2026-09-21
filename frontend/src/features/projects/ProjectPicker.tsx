import { useMemo, useState } from 'react'
import type { Project } from '../../api/projects'

export function ProjectPicker({ projects, value, onChange, emptyLabel = '未分类', currentId }: {
  projects: Project[]; value: string; onChange: (value: string) => void
  emptyLabel?: string; currentId?: string | null
}) {
  const [query, setQuery] = useState('')
  const options = useMemo(() => projects.filter(project =>
    project.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())
    && (project.status === 'ACTIVE' || project.id === currentId)).slice(0, 50), [currentId, projects, query])
  return <div className="project-picker" data-testid="project-picker">
    <input aria-label="搜索项目" value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索项目" />
    <select aria-label="选择项目" size={Math.min(6, options.length + 1)} value={value} onChange={event => onChange(event.target.value)}>
      <option value="">{emptyLabel}</option>
      {options.map(project => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}
    </select>
  </div>
}
