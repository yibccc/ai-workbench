import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import {
  createWeeklyReport, fetchDailyReport, fetchWeeklyReports, saveDailyReport,
  saveReportManualAdditions, type DailyReport, type ReportSourceRole,
} from './api'

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
})

const roleLabel: Record<ReportSourceRole, string> = {
  DAILY_RECORD: '当日事实', DAILY_TASK: '当日计划', WEEK_RECORD: '本周事实',
  CURRENT_TASK: '本周活动待办', NEXT_WEEK_TASK: '下周明确计划',
}

export function WeeklyReportPanel({ date, onDateChange, onDirtyChange }: {
  date: string
  onDateChange: (date: string) => void
  onDirtyChange: (dirty: boolean) => void
}) {
  const [reports, setReports] = useState<DailyReport[]>([])
  const [selected, setSelected] = useState<DailyReport | null>(null)
  const [draft, setDraft] = useState('')
  const [manualDraft, setManualDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const active = useRef<AbortController | null>(null)
  const dirty = selected?.status === 'SUCCEEDED'
    && (draft !== selected.content || manualDraft !== selected.manualAdditions)

  const select = useCallback((report: DailyReport) => {
    setSelected(report); setDraft(report.content); setManualDraft(report.manualAdditions)
    setReports((items) => items.some((item) => item.id === report.id)
      ? items.map((item) => item.id === report.id ? report : item) : [report, ...items])
  }, [])
  const load = useCallback(async (signal?: AbortSignal) => {
    const next = await fetchWeeklyReports(date, signal); setReports(next)
    if (next.length) select(next[0]); else { setSelected(null); setDraft(''); setManualDraft('') }
  }, [date, select])

  useEffect(() => { const controller = new AbortController()
    // Loading is asynchronous; state is updated only after the HTTP response resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load(controller.signal).catch((caught: unknown) => {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : '无法读取周报')
    }); return () => controller.abort() }, [load])
  useEffect(() => () => active.current?.abort(), [])
  useEffect(() => { active.current?.abort() }, [date])
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])
  useEffect(() => { if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const confirmDiscard = () => !dirty || window.confirm('周报还有未保存修改，确定放弃吗？')
  const generate = async () => {
    if (!confirmDiscard()) return
    active.current?.abort(); const controller = new AbortController(); active.current = controller
    setBusy(true); setError(null); setMessage(null)
    try {
      let report = await createWeeklyReport(date, crypto.randomUUID(), controller.signal); select(report)
      for (let attempt = 0; report.status === 'PROCESSING' && attempt < 20; attempt += 1) {
        await wait(800, controller.signal); report = await fetchDailyReport(report.id, controller.signal); select(report)
      }
      if (report.status === 'PROCESSING') throw new Error('周报仍在后台生成，请稍后查看该版本')
      if (report.status === 'FAILED') throw new Error(report.errorMessage ?? '周报生成失败')
      setMessage('周报新版本已生成，来源快照和旧版本均已保留。'); await load(controller.signal)
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : '周报生成失败')
    } finally { if (active.current === controller) { active.current = null; setBusy(false) } }
  }
  const saveBody = (event: FormEvent) => {
    event.preventDefault(); if (!selected) return
    const pendingManualDraft = manualDraft
    setBusy(true); setError(null)
    void saveDailyReport(selected.id, draft, selected.version).then((saved) => {
      select(saved); setManualDraft(pendingManualDraft); setMessage('AI 正文已保存。')
    })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '保存失败')).finally(() => setBusy(false))
  }
  const saveManual = () => {
    if (!selected) return
    const pendingBodyDraft = draft
    setBusy(true); setError(null)
    void saveReportManualAdditions(selected.id, manualDraft, selected.version).then((saved) => {
      select(saved); setDraft(pendingBodyDraft); setMessage('用户补充已独立保存，不会显示为 AI 有来源内容。')
    }).catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '保存失败')).finally(() => setBusy(false))
  }
  const copy = async () => {
    const combined = manualDraft.trim() ? `${draft}\n\n## 用户补充（无 AI 来源标记）\n${manualDraft.trim()}` : draft
    try { await navigator.clipboard.writeText(combined); setMessage('已复制周报正文及明确标记的用户补充。') }
    catch { setError('复制失败，请手动选择内容复制') }
  }

  return <article className="panel daily-report weekly-report">
    <div className="section-heading"><div><p className="kicker">WEEKLY REPORT</p><h2>自然周汇总</h2></div><input aria-label="周报所在日期" type="date" value={date} onChange={(event) => onDateChange(event.target.value)} /></div>
    <div className="report-toolbar"><button disabled={busy} type="button" onClick={() => void generate()}>{busy ? '生成中…' : '生成周报新版本'}</button><select aria-label="周报历史版本" value={selected?.id ?? ''} onChange={(event) => { const report = reports.find((item) => item.id === event.target.value); if (report && confirmDiscard()) select(report) }}><option value="">{reports.length ? '选择历史版本' : '暂无历史版本'}</option>{reports.map((report) => <option key={report.id} value={report.id}>{new Date(report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {report.status}</option>)}</select></div>
    {error && <p className="inline-notice inline-error" role="alert">{error}</p>}{message && <p className="inline-notice inline-success" role="status">{message}</p>}
    {selected && <><p className="report-meta">{selected.date} 至 {selected.periodEnd}（结束日排他） · 版本 {selected.version} · {selected.sources.length} 个冻结来源{selected.previousReportId ? ' · 基于上一版本重新生成' : ''}</p>
      {selected.status === 'SUCCEEDED' && <><form onSubmit={saveBody}><label>AI 周报正文<textarea rows={16} maxLength={20000} value={draft} onChange={(event) => setDraft(event.target.value)} /></label><div className="actions"><button disabled={busy || draft === selected.content} type="submit">保存 AI 正文</button><button className="secondary" type="button" onClick={() => void copy()}>复制组合周报</button></div></form><label>用户补充（不会附加 AI 来源）<textarea rows={5} maxLength={20000} value={manualDraft} onChange={(event) => setManualDraft(event.target.value)} /></label><button disabled={busy || manualDraft === selected.manualAdditions} type="button" onClick={saveManual}>独立保存用户补充</button></>}
      {selected.status === 'PROCESSING' && <p className="capture-state">来源已冻结，AI 正在生成本版本。</p>}{selected.status === 'FAILED' && <p className="inline-notice inline-error">{selected.errorMessage}</p>}
      {selected.editedAt && <p className="muted">AI 正文已由用户编辑；来源标记不会因人工修改自动重算。</p>}
      <details className="report-sources"><summary>核对本版本来源（{selected.sources.length}）</summary>{selected.sources.length === 0 ? <p className="muted">本周期没有可用于周报的有效事实或明确计划。</p> : selected.sources.map((source, index) => <article key={source.id}><strong>来源 {index + 1} · {roleLabel[source.role]} · {source.projectName ?? '未归属项目'}</strong><p>{source.content}</p><small>{new Date(source.sourceTime).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {source.status}</small></article>)}</details>
    </>}
  </article>
}
