export function Pagination({ page, totalPages, size, onPage, onSize, loading = false }: {
  page: number; totalPages: number; size: number
  onPage: (page: number) => void; onSize?: (size: number) => void
  loading?: boolean
}) {
  return <nav className="pagination" aria-label="分页" aria-busy={loading}>
    <button className="secondary" disabled={loading || page <= 0} type="button" onClick={() => onPage(page - 1)}>上一页</button>
    <span>第 {totalPages === 0 ? 0 : page + 1} / {totalPages} 页</span>
    <button className="secondary" disabled={loading || page + 1 >= totalPages} type="button" onClick={() => onPage(page + 1)}>下一页</button>
    {onSize && <label>每页<select disabled={loading} value={size} onChange={event => onSize(Number(event.target.value))}><option>10</option><option>20</option><option>50</option></select></label>}
    {loading && <span role="status">正在加载…</span>}
  </nav>
}
