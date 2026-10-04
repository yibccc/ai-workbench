import type { Account } from '../../api/auth'
import type { PostType } from '../../api/community'
import type { ViewId } from '../../components/layout/routes'
import { CommunityDetail, CommunityFeed, MyPublications, Unavailable } from './CommunityPages'
import { Button, CommunityIcon, Heading } from './ui'
import { SourcePicker } from '../publishing/SourcePicker'
import { PublishingEditor } from '../publishing/PublishingEditor'
import type { NavigationGuard } from '../../components/layout/navigationGuard'
import './community.css'
import './integration.css'

export function CommunityWorkspace({ account, page, navigate, registerGuard }: { account: Account; page: ViewId; navigate: (next: ViewId) => void; registerGuard: (guard: NavigationGuard | null) => void }) {
  const editor = page.startsWith('/publishing/posts/') || /^\/publishing\/new\/(DAILY|MOMENT|BLOG)$/.test(page)
  const postId = page.startsWith('/publishing/posts/') ? page.split('/')[3] : undefined
  const initialType = page.startsWith('/publishing/new/') ? page.split('/')[3] as PostType : undefined
  return <div className="page-main community-page-main">
    {page === '/community' && <CommunityFeed account={account} />}
    {page.startsWith('/community/posts/') && <CommunityDetail key={page} id={page.split('/')[3]} account={account} />}
    {page.startsWith('/community/authors/') && <CommunityFeed key={page} account={account} authorId={page.split('/')[3]} />}
    {page === '/publishing' && <MyPublications />}
    {page === '/publishing/sources' && <SourcePicker onCreated={id => navigate(`/publishing/posts/${id}`)} />}
    {page === '/publishing/new' && <><Heading eyebrow="PUBLISHING" title="选择一种分享方式" description="从一条进展、一点发现，或一篇完整的文章开始。" /><div className="stack compose-choices">{([['DAILY', 'calendar', '分享今天', '从自己的记录中勾选素材，再整理成分享稿。'], ['MOMENT', 'edit', '发一条动态', '不需要标题，写下一个发现或一段近况。'], ['BLOG', 'notebook', '写一篇博客', '用 Markdown 写作，插入图片并附上 PDF 或 MD 文件。']] as const).map(([type, icon, label, description]) => <div className="card pad spread" key={type}><div><h2 className="flex"><CommunityIcon name={icon} />{label}</h2><p className="muted compose-description">{description}</p></div><Button className="soft" aria-label={label} onClick={() => navigate(type === 'DAILY' ? '/publishing/sources' : `/publishing/new/${type}`)}><CommunityIcon name="arrow-right" /></Button></div>)}</div></>}
    {editor && <PublishingEditor account={account} postId={postId} initialType={initialType} routePreview={page.endsWith('/preview')} navigate={navigate} registerGuard={registerGuard} />}
    {page === 'unavailable' && <Unavailable />}
  </div>
}
