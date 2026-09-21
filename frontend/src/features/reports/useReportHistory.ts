import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchDailyReport } from '../../api/reports'
import { fetchReportPage, type DailyReport } from '../../api/reports'

/** One request owns the history page and detail together; cleanup prevents late responses winning. */
export function useReportHistory(type: 'DAILY' | 'WEEKLY', date: string,
  onSelect: (report: DailyReport | null) => void, onError: (message: string | null) => void) {
  const dateRef = useRef(date)
  const [query, setQuery] = useState({ date, page: 0, size: 20, revision: 0, preferredId: '' })
  const page = query.date === date ? query.page : 0
  const [reports, setReports] = useState<DailyReport[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const setPage = useCallback((page: number) => setQuery(q => ({ ...q, date, page, preferredId: '' })), [date])
  const setSize = useCallback((size: number) => setQuery(q => ({ ...q, date, size, page: 0, preferredId: '' })), [date])
  const refresh = useCallback((preferredId = '') => setQuery(q => ({ ...q, date, page: 0,
    preferredId, revision: q.revision + 1 })), [date])
  const selectReport = useCallback((preferredId: string) => setQuery(q => ({ ...q, date,
    page: q.date === date ? q.page : 0, preferredId, revision: q.revision + 1 })), [date])

  useEffect(() => {
    const controller = new AbortController()
    const load = async () => {
      setLoading(true); onError(null)
      if (dateRef.current !== date) { dateRef.current = date; setReports([]); setTotal(0); setTotalPages(0); onSelect(null) }
      try {
        const result = await fetchReportPage(type, date, page, query.size, controller.signal)
        const preferred = result.items.find(item => query.date === date && item.id === query.preferredId) ?? result.items[0]
        const detail = preferred ? await fetchDailyReport(preferred.id, controller.signal) : null
        if (controller.signal.aborted) return
        setReports(result.items); setTotal(result.totalElements); setTotalPages(result.totalPages)
        onSelect(detail)
      } catch (caught) {
        if (!controller.signal.aborted) onError(caught instanceof Error ? caught.message : '无法读取报告')
      } finally { if (!controller.signal.aborted) setLoading(false) }
    }
    void load()
    return () => controller.abort()
  }, [type, date, page, query, onSelect, onError])

  return { reports, setReports, page, size: query.size, total, totalPages, loading,
    setPage, setSize, refresh, selectReport }
}
