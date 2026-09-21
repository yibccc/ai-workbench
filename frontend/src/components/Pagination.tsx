import { Icon } from './Icon'

export function Pagination({ page, totalPages, size, onPage, onSize, loading = false }: {
  page: number; totalPages: number; size: number; onPage: (page: number) => void
  onSize?: (size: number) => void; loading?: boolean
}) {
  if (!loading && totalPages === 0) return null
  return <nav className="pagination" aria-label="分页" aria-busy={loading}>
    <span className="pagination-summary">{loading ? '加载中…' : `第 ${page + 1} / ${totalPages} 页`}</span>
    <div className="pagination-controls">{onSize && <label>每页<select disabled={loading} value={size} onChange={event => onSize(Number(event.target.value))}><option>10</option><option>20</option><option>50</option></select>条</label>}
      <button className="icon-button secondary" aria-label="上一页" disabled={loading || page <= 0} type="button" onClick={() => onPage(page - 1)}><Icon name="chevron-left" size={15} /></button>
      <button className="icon-button secondary" aria-label="下一页" disabled={loading || page + 1 >= totalPages} type="button" onClick={() => onPage(page + 1)}><Icon name="chevron-right" size={15} /></button>
    </div>
  </nav>
}
