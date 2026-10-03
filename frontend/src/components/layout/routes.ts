import { navigation, type PageId } from './navigation'
import type { PostType } from '../../api/community'

export type ViewId = PageId | 'users' | 'unavailable' | '/community' | '/publishing' | '/publishing/sources' | '/publishing/new'
  | `/community/posts/${string}` | `/community/authors/${string}` | `/publishing/new/${PostType}` | `/publishing/posts/${string}`
const uuid = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
const communityTarget = new RegExp(`^/(?:community(?:/(?:posts|authors)/${uuid})?|publishing(?:/(?:sources|new(?:/(?:DAILY|MOMENT|BLOG))?|posts/${uuid}(?:/preview)?))?)$`)
export function parseHash(hash: string): ViewId {
  const value = hash.startsWith('#') ? hash.slice(1) : hash
  if (!value) return 'records'
  if (value === 'users' || navigation.some(item => item.id === value)) return value as ViewId
  if (communityTarget.test(value)) return value as ViewId
  return 'unavailable'
}
export const isCommunityView = (view: ViewId) => view.startsWith('/') || view === 'unavailable'
export function routeTitle(view: ViewId) {
  if (view === 'users') return '用户管理'
  if (view === 'unavailable') return '内容不可访问'
  if (view.startsWith('/community/posts/')) return '内容详情'
  if (view.startsWith('/community/authors/')) return '作者主页'
  if (view === '/community') return '内容广场'
  if (view === '/publishing') return '我的发布'
  if (view === '/publishing/sources') return '选择今日素材'
  if (view === '/publishing/new') return '选择发布类型'
  if (view.endsWith('/preview')) return '发布前预览'
  if (view.startsWith('/publishing/')) return '发布编辑器'
  return navigation.find(item => item.id === view)?.label ?? '内容不可访问'
}
