import { request, requestBlob } from './http'
import { WORKSPACE_PAGE_SIZE, type PageResponse } from './pagination'

export type PostType = 'DAILY' | 'MOMENT' | 'BLOG'
export type PublicAuthor = { id: string; nickname: string; bio: string }
export type AttachmentInfo = {
  id: string; kind: 'IMAGE' | 'PDF' | 'MD'; fileName: string; contentType: string; size: number
  state: 'UPLOADING' | 'READY' | 'FAILED' | 'DELETING' | 'DELETE_FAILED' | 'DELETED'; safeFailureCode?: string | null
}
export type PostCard = {
  id: string; type: PostType; businessDate: string | null; title: string; summary: string
  firstPublishedAt: string; publishedAt: string; revisionId: string; revisionNo: number
  author: PublicAuthor; attachments: AttachmentInfo[]
}
export type PostDetail = PostCard & { bodyMarkdown: string }
export type PublicProfile = { nickname: string; bio: string; version: number }

export const fetchCommunityPage = (type: PostType | '', authorId: string | undefined, page: number, signal?: AbortSignal) => {
  const query = new URLSearchParams({ page: String(page), size: String(WORKSPACE_PAGE_SIZE) })
  if (type) query.set('type', type)
  if (authorId) query.set('authorId', authorId)
  return request<PageResponse<PostCard>>(`/api/community/posts/page?${query}`, { signal })
}
export const fetchCommunityPost = (id: string, signal?: AbortSignal) => request<PostDetail>(`/api/community/posts/${id}`, { signal })
export const fetchAuthor = (id: string, signal?: AbortSignal) => request<PublicAuthor>(`/api/community/authors/${id}`, { signal })
export const fetchOwnProfile = (signal?: AbortSignal) => request<PublicProfile>('/api/me/community/profile', { signal })
export const saveOwnProfile = (profile: PublicProfile) => request<PublicProfile>('/api/me/community/profile', { method: 'PUT', body: JSON.stringify(profile) })
export const hideCommunityPost = (id: string, expectedRevisionId: string, reason: string) => request<{ postId: string; status: 'HIDDEN' }>(`/api/admin/community/posts/${id}/hide`, { method: 'POST', body: JSON.stringify({ expectedRevisionId, reason }) })
export const readCommunityAttachment = (postId: string, attachmentId: string, signal?: AbortSignal) => requestBlob(`/api/community/posts/${postId}/attachments/${attachmentId}`, { signal })
