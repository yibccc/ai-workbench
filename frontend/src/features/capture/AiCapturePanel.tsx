import { type FormEvent, useEffect, useRef, useState } from 'react'
import { createCaptureInput } from '../../api/capture'
import { fetchCaptureInput, retryCaptureInput, revertCaptureInput, type CaptureInput } from '../../api/capture'
import { finishTracking, trackEntity, trackedIds } from '../../hooks/realtime'
import { useDialog } from '../../components/dialogContext'
import { Icon } from '../../components/Icon'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { useToast } from '../../components/toastContext'

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => {
    window.clearTimeout(timer)
    reject(new DOMException('Aborted', 'AbortError'))
  }, { once: true })
})

export function AiCapturePanel({ onGenerated, onEditRecord, onEditTask, onSummaryChange }: {
  onGenerated: (result: CaptureInput) => Promise<void>
  onEditRecord?: (id: string) => void
  onEditTask?: (id: string) => void
  onSummaryChange?: (summary?: string) => void
}) {
  const showDialog = useDialog()
  const { notify, toastRef } = useToast<HTMLElement>()
  const [content, setContent] = useState('')
  const [result, setResult] = useState<CaptureInput | null>(null)
  const [busy, setBusy] = useState(false)
  const activeRequest = useRef<AbortController | null>(null)

  useBeforeUnload(content.trim().length > 0)
  useEffect(() => () => activeRequest.current?.abort(), [])
  useEffect(() => {
    onSummaryChange?.(result?.status === 'PROCESSING' ? '处理中'
      : result?.status === 'FAILED' ? '处理失败'
        : result?.status === 'SUCCEEDED' ? `已生成 ${result.records.length + result.tasks.length} 项` : undefined)
  }, [onSummaryChange, result])
  useEffect(() => {
    const id = result?.status === 'PROCESSING' ? result.id : null
    if (!id) return
    return trackEntity('INPUT', id, () => {
      void fetchCaptureInput(id).then(current => {
        setResult(previous => previous?.id === current.id && previous.status !== 'PROCESSING'
          && current.status === 'PROCESSING' ? previous : current)
        if (current.status !== 'PROCESSING') {
          finishTracking('INPUT', id)
          if (current.status === 'SUCCEEDED') void onGenerated(current)
        }
      }).catch(() => undefined)
    })
  }, [onGenerated, result?.id, result?.status])
  useEffect(() => {
    const id = trackedIds('INPUT').at(-1)
    if (id) void fetchCaptureInput(id).then(setResult).catch(() => undefined)
  }, [])

  const poll = async (initial: CaptureInput, signal: AbortSignal) => {
    let current = initial
    for (let attempt = 0; current.status === 'PROCESSING' && attempt < 10; attempt += 1) {
      await wait(800, signal)
      current = await fetchCaptureInput(current.id, signal)
      setResult(current)
    }
    if (current.status === 'PROCESSING') {
      notify('原文已保存，正在后台处理；完成后会自动显示，无需刷新。')
      return current
    }
    if (current.status === 'SUCCEEDED') await onGenerated(current)
    finishTracking('INPUT', current.id)
    return current
  }
  const run = async (action: (signal: AbortSignal) => Promise<CaptureInput>) => {
    if (activeRequest.current) return false
    const controller = new AbortController()
    activeRequest.current = controller
    setBusy(true)
    try {
      const initial = await action(controller.signal)
      setResult(initial)
      setResult(await poll(initial, controller.signal))
      return true
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) {
        notify(caught instanceof Error ? caught.message : 'AI 处理失败', 'error')
      }
      return false
    } finally {
      if (!controller.signal.aborted) setBusy(false)
      if (activeRequest.current === controller) activeRequest.current = null
    }
  }
  const revert = () => {
    if (!result) return
    void showDialog({ title: '撤销本次生成？', description: '本次 AI 输入生成的全部记录和待办将被撤销，原文和处理历史仍保留。', confirmLabel: '确认撤销', danger: true, onConfirm: async () => {
        const updated = await revertCaptureInput(result.id)
        setResult(updated)
        notify('整批生成内容已撤销，原文和处理历史仍保留。', 'success')
        await onGenerated(updated)
    } })
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (busy || !content.trim()) return
    const requestId = crypto.randomUUID()
    void run((signal) => createCaptureInput(requestId, content, signal)).then((succeeded) => {
      if (succeeded) setContent('')
    })
  }

  return <article ref={toastRef} className="panel ai-capture" data-testid="ai-capture">
    <div className="section-heading"><div><h2>今天，有什么进展？</h2><p className="field-hint">把完成的事和接下来的安排，一起写下来。</p></div>{result && <span className="capture-status">{({ PROCESSING: '处理中', FAILED: '处理失败', SUCCEEDED: '已整理', REVERTED: '已撤销' })[result.status]}</span>}</div>
    <form onSubmit={submit}>
      <label><span className="sr-only">自然语言输入</span><textarea data-testid="capture-content" required disabled={busy} maxLength={8000} rows={3} value={content} onChange={(event) => setContent(event.target.value)} onKeyDown={(event) => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); if (!busy) event.currentTarget.form?.requestSubmit() } }} placeholder="例如：今天完成了支付接口联调，明天下午前补回归测试，优先级高。" /></label>
      <div className="composer-footer"><span className="field-hint">提交后保存原文并生成记录 / 待办<span className="keyboard-hint">Ctrl / ⌘ + Enter</span></span><button data-testid="capture-submit" disabled={busy || !content.trim()} type="submit"><Icon name="sparkles" size={16} />{busy ? 'AI 正在整理…' : '保存并智能拆分'}</button></div>
    </form>
    {result?.status === 'PROCESSING' && <p className="capture-state">原文已保存，AI 正在处理。关闭页面也不会丢失原文。</p>}
    {result?.status === 'FAILED' && <div className="capture-state capture-failed" data-testid="capture-failed"><span>{result.errorMessage ?? '处理失败'}</span><button className="secondary" disabled={busy} type="button" onClick={() => void run((signal) => retryCaptureInput(result.id, signal))}>保留原时间基准重试</button></div>}
    {result?.status === 'SUCCEEDED' && <div className="capture-results">
      <p>已生成 {result.records.length} 条工作记录和 {result.tasks.length} 项待办。</p>
      {result.records.map((record) => <button className="result-link" type="button" onClick={() => onEditRecord?.(record.id)} key={record.id}>记录：{record.content}（加载编辑）</button>)}
      {result.tasks.map((task) => <button className="result-link" type="button" onClick={() => onEditTask?.(task.id)} key={task.id}>待办：{task.title}（加载编辑）</button>)}
      <button className="secondary" data-testid="capture-revert" disabled={busy} type="button" onClick={revert}>撤销本次全部生成内容</button>
    </div>}
    {result?.status === 'REVERTED' && <p className="capture-state">本批次已撤销；原文仍保留。如仍需录入，可切换到手工记录，或进入待办任务。</p>}
  </article>
}
