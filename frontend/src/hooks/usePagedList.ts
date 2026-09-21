import { useCallback, useEffect, useRef, useState } from 'react'
import type { PageResponse } from '../api/pagination'

/** Query changes and mutation refreshes share one cancellable request owner. */
export function usePagedList<T>(fetchPage: (signal: AbortSignal) => Promise<PageResponse<T>>,
  onError: (message: string) => void, onPage: (page: number) => void, refreshKey?: unknown) {
  const [result, setResult] = useState<{ query: typeof fetchPage; data: PageResponse<T> } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const current = useRef(fetchPage)
  const active = useRef<AbortController | null>(null)
  const mounted = useRef(false)
  const refresh = useCallback(async () => {
    if (!mounted.current) return
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    const query = current.current
    setLoading(true); setError(null)
    try {
      const data = await query(controller.signal)
      if (controller.signal.aborted || active.current !== controller) return
      setResult({ query, data })
      if (data.page > 0 && data.items.length === 0) onPage(Math.max(0, data.totalPages - 1))
    } catch (caught) {
      if (!controller.signal.aborted) {
        const message = caught instanceof Error ? caught.message : '无法读取列表'
        setError(message); onError(message)
      }
    } finally {
      if (active.current === controller) { active.current = null; setLoading(false) }
    }
  }, [onError, onPage])
  useEffect(() => {
    mounted.current = true
    current.current = fetchPage
    void refresh()
    return () => { mounted.current = false; active.current?.abort(); active.current = null }
  }, [fetchPage, refresh, refreshKey])
  // Never display the previous date/filter's rows under a new heading.
  return { data: result?.query === fetchPage ? result.data : null, loading, error, refresh }
}
