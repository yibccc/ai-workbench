import { Icon } from './Icon'

export function Pagination({ page, totalPages, onPage, loading = false }: {
  page: number; totalPages: number; onPage: (page: number) => void
  loading?: boolean
}) {
  if (!loading && totalPages === 0) return null
  return <nav className="pagination" aria-label="分页" aria-busy={loading}>
    <span className="pagination-summary">{loading ? '加载中…' : `第 ${page + 1} / ${totalPages} 页`}</span>
    <div className="pagination-controls">
      <button className="icon-button secondary" aria-label="上一页" disabled={loading || page <= 0} type="button" onClick={() => onPage(page - 1)}><Icon name="chevron-left" size={15} /></button>
      <button className="icon-button secondary" aria-label="下一页" disabled={loading || page + 1 >= totalPages} type="button" onClick={() => onPage(page + 1)}><Icon name="chevron-right" size={15} /></button>
    </div>
  </nav>
}
