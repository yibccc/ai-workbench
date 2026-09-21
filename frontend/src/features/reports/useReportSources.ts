import { useEffect, useState } from 'react'
import { fetchReportSourcePage } from '../../api/reports'
import { type DailyReport } from '../../api/reports'

export function useReportSources(id: string | undefined, sourceCount: number | undefined,
  onError: (message: string) => void) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [pages, setPages] = useState(0)
  const [items, setItems] = useState<DailyReport['sources']>([])
  const [resultKey, setResultKey] = useState('')
  const [loading, setLoading] = useState(false)
  const key = id ? `${id}:${page}:${size}` : ''
  useEffect(() => {
    const controller = new AbortController()
    if (!id) return
    const load = async () => {
      setLoading(true)
      try {
        const result = await fetchReportSourcePage(id, page, size, controller.signal)
        if (controller.signal.aborted) return
        setItems(result.items); setPages(result.totalPages); setResultKey(`${id}:${page}:${size}`)
      } catch (caught) {
        if (!controller.signal.aborted) onError(caught instanceof Error ? caught.message : '无法读取来源')
      } finally { if (!controller.signal.aborted) setLoading(false) }
    }
    void load()
    return () => controller.abort()
  }, [id, sourceCount, page, size, onError])
  // An aborted request may retain internal state; never expose it for another report/page.
  return { page, size, pages: id ? pages : 0, items: key && resultKey === key ? items : [],
    loading: Boolean(id) && loading, setPage,
    reset: () => { setPage(0); setItems([]) },
    setSize: (value: number) => { setPage(0); setSize(value) } }
}
