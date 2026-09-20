import { type FormEvent, useEffect, useRef, useState } from 'react'
import { createCaptureInput, fetchCaptureInput, retryCaptureInput, revertCaptureInput, type CaptureInput } from './api'

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => {
    window.clearTimeout(timer)
    reject(new DOMException('Aborted', 'AbortError'))
  }, { once: true })
})

export function AiCapturePanel({ onGenerated }: { onGenerated: (result: CaptureInput) => Promise<void> }) {
  const [content, setContent] = useState('')
  const [result, setResult] = useState<CaptureInput | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const activeRequest = useRef<AbortController | null>(null)

  useEffect(() => () => activeRequest.current?.abort(), [])

  const poll = async (initial: CaptureInput, signal: AbortSignal) => {
    let current = initial
    for (let attempt = 0; current.status === 'PROCESSING' && attempt < 10; attempt += 1) {
      await wait(800, signal)
      current = await fetchCaptureInput(current.id, signal)
      setResult(current)
    }
    if (current.status === 'PROCESSING') throw new Error('原文已保存，AI 仍在后台处理，请稍后刷新页面查看')
    if (current.status === 'SUCCEEDED') await onGenerated(current)
    return current
  }
  const run = async (action: (signal: AbortSignal) => Promise<CaptureInput>) => {
    activeRequest.current?.abort()
    const controller = new AbortController()
    activeRequest.current = controller
    setBusy(true); setError(null); setMessage(null)
    try {
      setResult(await poll(await action(controller.signal), controller.signal))
      return true
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) {
        setError(caught instanceof Error ? caught.message : 'AI 处理失败')
      }
      return false
    } finally {
      if (!controller.signal.aborted) setBusy(false)
      if (activeRequest.current === controller) activeRequest.current = null
    }
  }
  const revert = () => {
    if (!result || !window.confirm('撤销这次 AI 输入生成的全部记录和待办？')) return
    void run((signal) => revertCaptureInput(result.id, signal)).then(async (succeeded) => {
      if (succeeded) {
        setMessage('整批生成内容已撤销，原文和处理历史仍保留。')
        if (result) await onGenerated({ ...result, records: [], tasks: [], status: 'REVERTED' })
      }
    })
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const requestId = crypto.randomUUID()
    void run((signal) => createCaptureInput(requestId, content, signal)).then((succeeded) => {
      if (succeeded) setContent('')
    })
  }

  return <article className="panel ai-capture">
    <div className="section-heading"><div><p className="kicker">AI CAPTURE</p><h2>一句话记录与安排</h2></div>{result && <span>第 {result.attemptCount} 次 · {result.status}</span>}</div>
    <form onSubmit={submit}>
      <label>自然语言输入<textarea required maxLength={8000} rows={4} value={content} onChange={(event) => setContent(event.target.value)} placeholder="例如：今天完成了支付接口联调，明天下午前补回归测试，高优先级" /></label>
      <div className="actions"><button disabled={busy} type="submit">{busy ? 'AI 正在拆分…' : '保存原文并自动拆分'}</button></div>
    </form>
    {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
    {message && <p className="inline-notice" role="status">{message}</p>}
    {result?.status === 'PROCESSING' && <p className="capture-state">原文已保存，AI 正在处理。关闭页面也不会丢失原文。</p>}
    {result?.status === 'FAILED' && <div className="capture-state capture-failed"><span>{result.errorMessage ?? '处理失败'}</span><button className="secondary" disabled={busy} type="button" onClick={() => void run((signal) => retryCaptureInput(result.id, signal))}>沿用原基准重试</button></div>}
    {result?.status === 'SUCCEEDED' && <div className="capture-results">
      <p>已生成 {result.records.length} 条工作记录和 {result.tasks.length} 项待办。</p>
      {result.records.map((record) => <a href={`#record-${record.id}`} key={record.id}>记录：{record.content}（查看并编辑）</a>)}
      {result.tasks.map((task) => <a href={`#task-${task.id}`} key={task.id}>待办：{task.title}（查看并编辑）</a>)}
      <button className="secondary" disabled={busy} type="button" onClick={revert}>撤销本次全部生成内容</button>
    </div>}
    {result?.status === 'REVERTED' && <p className="capture-state">本批次已撤销；原文仍保留。如仍需录入，可使用下方手工记录或待办入口。</p>}
  </article>
}
