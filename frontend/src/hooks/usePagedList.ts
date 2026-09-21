import { useCallback, useEffect, useRef, useState } from 'react'
import type { PageResponse } from '../api/pagination'

/** Query changes and mutation refreshes share one cancellable request owner. */
export function usePagedList<T>(fetchPage: (signal: AbortSignal) => Promise<PageResponse<T>>,
  onError: (message: string) => void, onPage: (page: number) => void, refreshKey?: unknown) {
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(false)
  const current = useRef(fetchPage)
  const active = useRef<AbortController | null>(null)
  const mounted = useRef(false)
  const refresh = useCallback(async () => {
    if (!mounted.current) return
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    setLoading(true)
    try {
      const result = await current.current(controller.signal)
      if (controller.signal.aborted || active.current !== controller) return
      setData(result)
      if (result.page > 0 && result.items.length === 0) onPage(Math.max(0, result.totalPages - 1))
    } catch (caught) {
      if (!controller.signal.aborted) onError(caught instanceof Error ? caught.message : '无法读取列表')
    } finally {
      if (active.current === controller) { active.current = null; setLoading(false) }
    }
  }, [onError, onPage])
  useEffect(() => {
    mounted.current = true
    current.current = fetchPage
    // Fetch lifecycle synchronizes this server-side query, including mutation refreshes.
    void refresh()
    return () => { mounted.current = false; active.current?.abort(); active.current = null }
  }, [fetchPage, refresh, refreshKey])
  return { data, loading, refresh }
}
