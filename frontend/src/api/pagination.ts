export const WORKSPACE_PAGE_SIZE = 5

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
