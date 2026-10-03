import { request, requestBlob } from './http'
import { WORKSPACE_PAGE_SIZE, type PageResponse } from './pagination'
import type { AttachmentInfo, PostDetail, PostType } from './community'

export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN' | 'HIDDEN'
export type DraftInput = { type: PostType; businessDate: string | null; title: string; summary: string; bodyMarkdown: string; attachmentIds: string[] }
export type Moderation = { reason: string; occurredAt: string }
export type OwnerPost = {
  postId: string; type: PostType; status: PostStatus; version: number
  draft: DraftInput & { savedAt: string; attachments: AttachmentInfo[] }
  attachments: AttachmentInfo[]; currentPublished: PostDetail | null; moderation: Moderation | null
}
export type OwnerPostCard = {
  postId: string; type: PostType; status: PostStatus; version: number; businessDate: string | null
  title: string; summary: string; updatedAt: string; firstPublishedAt: string | null
  currentRevisionId: string | null; currentRevisionNo: number | null; hasUnpublishedChanges: boolean; moderation: Moderation | null
}
export type Material = { id: string; source: 'MANUAL' | 'TASK_COMPLETION' | 'FOCUS_SESSION'; content: string; completionResult: string | null; progress: string | null; focusMs: number | null; projectName: string | null; occurredAt: string }
export type SourceField = 'CONTENT' | 'COMPLETION_RESULT' | 'PROGRESS'
export type ShareInput = { date: string; selections: { recordId: string; fields: SourceField[] }[]; includeFocus: boolean }
export type PublishInput = DraftInput & { version: number; requestId: string; visibility: 'MEMBERS' }
export type PublishResult = { postId: string; version: number; revisionId: string; revisionNo: number; publishedAt: string; firstPublishedAt: string }
export type AttachmentMaintenance = { version: number; results: { attachmentId: string; state: AttachmentInfo['state']; safeFailureCode: string | null }[] }

export const fetchOwnPosts = (status: PostStatus | '', page: number, signal?: AbortSignal) => request<PageResponse<OwnerPostCard>>(`/api/me/posts/page?${new URLSearchParams({ ...(status ? { status } : {}), page: String(page), size: String(WORKSPACE_PAGE_SIZE) })}`, { signal })
export const fetchOwnPost = (id: string, signal?: AbortSignal) => request<OwnerPost>(`/api/me/posts/${id}`, { signal })
export const createPost = (input: Omit<DraftInput, 'attachmentIds'>) => request<OwnerPost>('/api/me/posts', { method: 'POST', body: JSON.stringify(input) })
export const savePost = (id: string, input: DraftInput & { version: number }) => request<{ postId: string; version: number; savedAt: string }>(`/api/me/posts/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const publishPost = (id: string, input: PublishInput) => request<PublishResult>(`/api/me/posts/${id}/publish`, { method: 'POST', body: JSON.stringify(input) })
export const withdrawPost = (id: string, version: number) => request<{ postId: string; status: PostStatus; version: number }>(`/api/me/posts/${id}/withdraw`, { method: 'POST', body: JSON.stringify({ version }) })
export const fetchMaterials = (date: string, page: number, signal?: AbortSignal) => request<PageResponse<Material>>(`/api/me/community/sources/page?${new URLSearchParams({ date, page: String(page), size: String(WORKSPACE_PAGE_SIZE) })}`, { signal })
export const composeShare = (input: ShareInput) => request<OwnerPost>('/api/me/community/share-drafts', { method: 'POST', body: JSON.stringify(input) })
export const uploadAttachment = (id: string, file: File, expectedVersion: number, requestId: string) => {
  const body = new FormData(); body.set('file', file); body.set('expectedVersion', String(expectedVersion)); body.set('requestId', requestId)
  return request<{ attachment: AttachmentInfo; version: number }>(`/api/me/posts/${id}/attachments`, { method: 'POST', body })
}
export const readOwnAttachment = (postId: string, attachmentId: string, signal?: AbortSignal) => requestBlob(`/api/me/posts/${postId}/attachments/${attachmentId}`, { signal })
export const recoverAttachments = (id: string) => request<AttachmentMaintenance>(`/api/me/posts/${id}/attachments/recover`, { method: 'POST' })
export const cleanupAttachments = (id: string) => request<AttachmentMaintenance>(`/api/me/posts/${id}/attachments/cleanup`, { method: 'POST' })
