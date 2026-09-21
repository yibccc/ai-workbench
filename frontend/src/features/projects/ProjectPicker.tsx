import type { Project } from '../../api/projects'

/** Native single-line control: keeps keyboard support and never silently truncates to 50 projects. */
export function ProjectPicker({ projects, value, onChange, emptyLabel = '未归属项目', currentId }: {
  projects: Project[]; value: string; onChange: (value: string) => void
  emptyLabel?: string; currentId?: string | null
}) {
  const options = projects.filter(project => project.status === 'ACTIVE' || project.id === currentId || project.id === value)
  return <select className="project-picker" data-testid="project-picker" value={value} onChange={event => onChange(event.target.value)}>
    <option value="">{emptyLabel}</option>
    {value && !options.some(project => project.id === value) && <option value={value}>当前项目（详情暂不可用）</option>}
    {options.map(project => <option key={project.id} value={project.id}>{project.name}{project.status === 'ARCHIVED' ? '（已归档）' : ''}</option>)}
  </select>
}
