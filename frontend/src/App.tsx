import { useCallback, useEffect, useState } from 'react'
import { fetchWorkbenchStatus, type WorkbenchStatus } from './api'

const labels: Record<string, string> = {
  backend: '后端 API',
  postgres: 'PostgreSQL',
  redis: 'Redis',
  deepseek: 'DeepSeek',
}

function App() {
  const [status, setStatus] = useState<WorkbenchStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      setStatus(await fetchWorkbenchStatus(signal))
      setError(null)
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') return
      setError(caught instanceof Error ? caught.message : '无法读取运行状态')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void fetchWorkbenchStatus(controller.signal)
      .then((nextStatus) => {
        setStatus(nextStatus)
        setError(null)
      })
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        setError(caught instanceof Error ? caught.message : '无法读取运行状态')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="shell">
      <header className="hero">
        <p className="eyebrow">LOCAL CONTROL PLANE</p>
        <h1>AI Workbench</h1>
        <p className="lede">技术底座状态一览。模型探针仅在后端显式调用，不会在打开页面时产生费用。</p>
        <button type="button" onClick={() => void refresh()} disabled={loading}>
          {loading ? '检查中…' : '重新检查'}
        </button>
      </header>

      {error && <p className="error" role="alert">后端不可用：{error}</p>}

      <section className="grid" aria-label="组件状态">
        {status
          ? Object.entries(status.components).map(([name, component]) => (
              <article className="card" key={name}>
                <div className="card-title">
                  <h2>{labels[name] ?? name}</h2>
                  <span className={`badge badge-${component.status.toLowerCase()}`}>
                    {component.status}
                  </span>
                </div>
                <p>{component.detail}</p>
              </article>
            ))
          : !error && <p className="empty">正在建立本机连接…</p>}
      </section>

      {status && (
        <footer>
          <span>检查时间：{new Date(status.checkedAt).toLocaleString('zh-CN')}</span>
          <span>{Object.entries(status.versions).map(([key, value]) => `${key} ${value}`).join(' · ')}</span>
        </footer>
      )}
    </main>
  )
}

export default App
