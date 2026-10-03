import type { IconName } from '../../components/Icon'
import type { PostType } from '../../api/community'
import { WORKBENCH_TIME_ZONE } from '../../utils/date'

export const typeLabels: Record<PostType, string> = { DAILY: '今日分享', MOMENT: '动态', BLOG: '博客' }
export const typeIcons: Record<PostType, IconName> = { DAILY: 'calendar', MOMENT: 'edit', BLOG: 'notebook' }
export const bytesLabel = (bytes: number) => bytes < 1048576 ? `${Math.round(bytes / 1024)} KiB` : `${(bytes / 1048576).toFixed(2).replace(/\.00$/, '')} MiB`
export const dateLabel = (value: string) => new Intl.DateTimeFormat('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
export const errorMessage = (caught: unknown) => caught instanceof Error ? caught.message : '操作失败，请重试'
export const isAborted = (caught: unknown) => caught instanceof DOMException && caught.name === 'AbortError'
