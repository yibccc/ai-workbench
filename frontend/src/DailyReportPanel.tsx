import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createDailyReport, fetchDailyReport, fetchDailyReports, saveDailyReport, type DailyReport } from './api'

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
})

export function DailyReportPanel({ date, onDateChange, onDirtyChange }: {
  date: string
  onDateChange: (date: string) => void
  onDirtyChange: (dirty: boolean) => void
}) {
  const [reports, setReports] = useState<DailyReport[]>([])
  const [selected, setSelected] = useState<DailyReport | null>(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const active = useRef<AbortController | null>(null)
  const dirty = selected?.status === 'SUCCEEDED' && draft !== selected.content
  const select = (report: DailyReport) => {
    setSelected(report); setDraft(report.content)
    setReports((items) => items.some((item) => item.id === report.id)
      ? items.map((item) => item.id === report.id ? report : item)
      : [report, ...items])
  }
  const load = useCallback(async (signal?: AbortSignal) => {
    const next = await fetchDailyReports(date, signal); setReports(next)
    if (next.length) select(next[0]); else { setSelected(null); setDraft('') }
  }, [date])

  useEffect(() => { const controller = new AbortController()
    // Loading is asynchronous; state is updated only after the HTTP response resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load(controller.signal).catch((caught: unknown) => {
    if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : '无法读取日报')
  }); return () => controller.abort() }, [load])
  useEffect(() => () => { active.current?.abort(); active.current = null }, [])
  useEffect(() => { active.current?.abort() }, [date])
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const confirmDiscard = () => !dirty || window.confirm('日报正文尚未保存，确定放弃修改吗？')

  const generate = async () => {
    if (!confirmDiscard()) return
    active.current?.abort(); const controller = new AbortController(); active.current = controller
    setBusy(true); setError(null); setMessage(null)
    try {
      let report = await createDailyReport(date, crypto.randomUUID(), controller.signal); select(report)
      for (let attempt = 0; report.status === 'PROCESSING' && attempt < 15; attempt += 1) {
        await wait(800, controller.signal); report = await fetchDailyReport(report.id, controller.signal); select(report)
      }
      if (report.status === 'PROCESSING') throw new Error('日报仍在后台生成，请稍后重新选择该版本查看')
      if (report.status === 'FAILED') throw new Error(report.errorMessage ?? '日报生成失败')
      setMessage('日报已生成；修改正文后需要明确保存。'); await load(controller.signal)
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : '日报生成失败')
    } finally {
      if (active.current === controller) { active.current = null; setBusy(false) }
    }
  }
  const save = (event: FormEvent) => {
    event.preventDefault(); if (!selected) return
    setBusy(true); setError(null); setMessage(null)
    void saveDailyReport(selected.id, draft, selected.version).then((saved) => {
      select(saved); setReports((items) => items.map((item) => item.id === saved.id ? saved : item)); setMessage('日报正文已保存。')
    }).catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '保存失败')).finally(() => setBusy(false))
  }
  const copy = async () => {
    try { await navigator.clipboard.writeText(draft); setMessage('已复制当前编辑区内容（未自动保存）。') }
    catch { setError('复制失败，请手动选择正文复制') }
  }

  return <article className="panel daily-report">
    <div className="section-heading"><div><p className="kicker">DAILY REPORT</p><h2>当天汇总</h2></div><input aria-label="日报日期" type="date" value={date} onChange={(event) => onDateChange(event.target.value)} /></div>
    <div className="report-toolbar"><button disabled={busy} type="button" onClick={() => void generate()}>{busy ? '生成中…' : '手动生成新版本'}</button><select aria-label="日报历史版本" value={selected?.id ?? ''} onChange={(event) => { const report = reports.find((item) => item.id === event.target.value); if (report && confirmDiscard()) select(report) }}><option value="">{reports.length ? '选择历史版本' : '暂无历史版本'}</option>{reports.map((report) => <option key={report.id} value={report.id}>{new Date(report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {report.status}</option>)}</select></div>
    {error && <p className="inline-notice inline-error" role="alert">{error}</p>}{message && <p className="inline-notice inline-success" role="status">{message}</p>}
    {selected && <><p className="report-meta">{selected.status} · 版本 {selected.version} · {selected.sources.length} 个冻结来源{selected.editedAt ? ' · 已人工编辑' : ''}</p>
      {selected.status === 'SUCCEEDED' && <form onSubmit={save}><label>日报正文<textarea rows={14} maxLength={20000} value={draft} onChange={(event) => setDraft(event.target.value)} /></label><div className="actions"><button disabled={busy || draft === selected.content} type="submit">明确保存正文</button><button className="secondary" type="button" onClick={() => void copy()}>复制当前正文</button></div></form>}
      {selected.status === 'PROCESSING' && <p className="capture-state">来源已冻结，AI 正在生成本版本。</p>}{selected.status === 'FAILED' && <p className="inline-notice inline-error">{selected.errorMessage}</p>}
      {selected.editedAt && <p className="muted">正文已由用户编辑；“来源 N”仅代表生成时的 AI 引用，人工修改内容不自动继承该引用关系。</p>}
      <details className="report-sources"><summary>核对本版本来源（{selected.sources.length}）</summary>{selected.sources.length === 0 ? <p className="muted">该日期没有有效记录或已安排计划。</p> : selected.sources.map((source, index) => <article key={source.id}><strong>来源 {index + 1} · {source.type === 'RECORD' ? '记录' : '计划'} · {source.projectName ?? '未归属项目'}</strong><p>{source.content}</p><small>{new Date(source.sourceTime).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {source.status}</small></article>)}</details>
    </>}
  </article>
}
