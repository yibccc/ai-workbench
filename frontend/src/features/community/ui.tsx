import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Icon, type IconName } from '../../components/Icon'
import type { PostType, PublicAuthor } from '../../api/community'
import { typeLabels } from './model'
export type CommunityIconName = IconName | 'upload' | 'download' | 'image' | 'paperclip' | 'code' | 'user' | 'copy'
// R2 app.js ICONS are converted directly to SVG React elements, without HTML insertion.
const r2Icons: Partial<Record<CommunityIconName, ReactNode>> = {
  notebook: <path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1m0-15c3-2 6-2 9-1v15c-3-1-6-1-9 1V5Z" />,
  menu: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  report: <path d="M6 3h8l4 4v14H6V3Zm8 0v5h4M9 12h6m-6 4h6" />,
  archive: <><rect x="6" y="10" width="12" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></>,
  edit: <path d="m4 16 12-12 4 4L8 20H4v-4Zm10-10 4 4" />,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4m10-4v4M3 10h18m-13 4h2m4 0h2m-8 3h2" /></>,
  upload: <path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6" />,
  download: <path d="M12 3v13m-5-5 5 5 5-5M4 16v5h16v-5" />,
  image: <><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8" cy="8" r="1.5" /><path d="m3 17 5-5 4 3 4-6 5 7" /></>,
  paperclip: <path d="m8 13 7-7a3 3 0 0 1 4 4l-9 9a5 5 0 0 1-7-7L13 2m-6 12 7-7" />,
  code: <path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-14-2 16" />,
  user: <><circle cx="12" cy="7" r="4" /><path d="M4 22v-3a8 8 0 0 1 16 0v3" /></>,
  copy: <><rect x="8" y="8" width="13" height="13" rx="1" /><path d="M16 8V3H3v13h5" /></>,
}
export function CommunityIcon({ name }: { name: CommunityIconName }) {
  const original = r2Icons[name]
  return original ? <svg className="icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">{original}</svg> : <Icon name={name as IconName} className="icon" />
}
export function Button({ children, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) { return <button type="button" className={`btn ${className}`} {...props}>{children}</button> }
export function Heading({ eyebrow, title, description, extra }: { eyebrow: string; title: string; description: string; extra?: ReactNode }) {
  return <div className="page-heading"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p className="desc">{description}</p></div>{extra ?? <div className="visibility"><CommunityIcon name="archive" />仅本站登录用户可见</div>}</div>
}
export function Notice({ children, kind = '' }: { children: ReactNode; kind?: 'error' | 'warning' | 'success' | '' }) { return <div className={`notice ${kind}`} role={kind === 'error' ? 'alert' : 'note'}><CommunityIcon name={kind === 'error' ? 'alert' : 'check'} /><span>{children}</span></div> }
export function Empty({ title, description, children }: { title: string; description: string; children?: ReactNode }) { return <div className="card empty-state"><div className="empty-icon"><CommunityIcon name="report" /></div><h2>{title}</h2><p>{description}</p>{children}</div> }
export function LoadState({ loading, error, retry }: { loading: boolean; error: string | null; retry: () => void }) {
  return loading ? <div className="card empty-state" role="status">正在加载…</div> : error ? <Empty title="内容暂时没有加载出来" description={error}><Button className="secondary" onClick={retry}>重新加载</Button></Empty> : null
}
export function Author({ author, subtitle }: { author: PublicAuthor; subtitle: string }) { return <div className="author"><div className="avatar">{author.nickname.slice(0, 1)}</div><div><a className="author-name" href={`#/community/authors/${author.id}`}>{author.nickname}</a><small>{subtitle}</small></div></div> }
export function FeedTabs({ value, onChange }: { value: PostType | ''; onChange: (type: PostType | '') => void }) { return <div className="feed-tabs"><div className="tabs" aria-label="内容分类">{(['', 'DAILY', 'MOMENT', 'BLOG'] as const).map(type => <button key={type} type="button" className={`tab ${value === type ? 'active' : ''}`} aria-pressed={value === type} onClick={() => onChange(type)}>{type ? typeLabels[type] : '全部'}</button>)}</div><span className="subtle"><CommunityIcon name="calendar" /> 最新发布</span></div> }
export function Stepper({ active }: { active: number }) { return <div className="stepper card" aria-label="发布步骤">{['选择素材', '编辑分享', '预览发布'].map((label, index) => <span className="step-group" key={label}>{index > 0 && <span className="step-line" />}<span className={`step ${index === active ? 'active' : index < active ? 'done' : ''}`}><span className="step-num">{index < active ? '✓' : `0${index + 1}`}</span><span>{label}</span></span></span>)}</div> }
export function Pager({ page, totalPages, total, onPage, loading }: { page: number; totalPages: number; total: number; onPage: (page: number) => void; loading: boolean }) { return <div className="pager"><span>共 {total} 条</span><div className="flex"><Button className="ghost sm" disabled={loading || page === 0} onClick={() => onPage(page - 1)}>上一页</Button><span>{page + 1} / {Math.max(1, totalPages)}</span><Button className="ghost sm" disabled={loading || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>下一页</Button></div></div> }
