import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import {
  createWeeklyReport } from '../../api/reports'
import { fetchDailyReport, saveDailyReport, saveReportManualAdditions, type DailyReport, type ReportSourceRole } from '../../api/reports'
import { finishTracking, trackEntity, trackedIds } from '../../hooks/realtime'
import { Pagination } from '../../components/Pagination'
import { useReportHistory } from './useReportHistory'
import { useReportSources } from './useReportSources'
import { useDialog } from '../../components/dialogContext'

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
})

const roleLabel: Record<ReportSourceRole, string> = {
  DAILY_RECORD: '当日事实', DAILY_TASK: '当日计划', WEEK_RECORD: '本周事实',
  CURRENT_TASK: '本周活动待办', NEXT_WEEK_TASK: '下周明确计划',
}
import { weekLabel, weekStart } from '../../utils/date'

export function WeeklyReportPanel({ date, onDateChange, onDirtyChange, onSummaryChange }: {
  date: string
  onDateChange: (date: string) => void
  onDirtyChange: (dirty: boolean) => void
  onSummaryChange?: (summary?: string) => void
}) {
  const showDialog = useDialog()
  const [selected, setSelected] = useState<DailyReport | null>(null)
  const [draft, setDraft] = useState('')
  const [manualDraft, setManualDraft] = useState('')
  const draftRef = useRef('')
  const manualRef = useRef('')
  const selectedRef = useRef<DailyReport | null>(null)
  const bodyDirtyRef = useRef(false)
  const manualDirtyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const active = useRef<AbortController | null>(null)
  const sourceReset = useRef<() => void>(() => undefined)
  const dirty = selected?.status === 'SUCCEEDED'
    && (draft !== selected.content || manualDraft !== selected.manualAdditions)

  const select = useCallback((report: DailyReport) => {
    setSelected(report); selectedRef.current = report
    setDraft(report.content); draftRef.current = report.content
    setManualDraft(report.manualAdditions); manualRef.current = report.manualAdditions
    bodyDirtyRef.current = false; manualDirtyRef.current = false
    sourceReset.current()
  }, [])
  const mergeRealtime = useCallback((report: DailyReport) => {
    const current = selectedRef.current
    if (current?.id === report.id && (report.version < current.version
        || (current.status !== 'PROCESSING' && report.status === 'PROCESSING'))) return
    const preserveBody = selectedRef.current?.id === report.id && bodyDirtyRef.current
    const preserveManual = selectedRef.current?.id === report.id && manualDirtyRef.current
    setSelected(report); selectedRef.current = report
    if (!preserveBody) { setDraft(report.content); draftRef.current = report.content }
    if (!preserveManual) { setManualDraft(report.manualAdditions); manualRef.current = report.manualAdditions }
  }, [])
  const onHistorySelect = useCallback((report: DailyReport | null) => {
    if (report) {
      if (selectedRef.current?.id === report.id) mergeRealtime(report)
      else select(report)
    } else { setSelected(null); selectedRef.current = null; setDraft(''); setManualDraft('') }
  }, [mergeRealtime, select])
  const { reports, page, size, total, totalPages, loading, setPage, setSize, refresh, selectReport } =
    useReportHistory('WEEKLY', date, onHistorySelect, setError)
  const sources = useReportSources(selected?.id, selected?.sourceCount, setError)
  useEffect(() => { sourceReset.current = sources.reset })
  useEffect(() => () => active.current?.abort(), [])
  useEffect(() => { active.current?.abort() }, [date])
  useEffect(() => {
    const id = selected?.status === 'PROCESSING' ? selected.id : null
    if (!id) return
    return trackEntity('REPORT', id, () => void fetchDailyReport(id).then(report => {
      if (selectedRef.current?.id !== id) return
      mergeRealtime(report)
      if (report.status !== 'PROCESSING') { finishTracking('REPORT', id); refresh(id) }
    }).catch(() => undefined))
  }, [mergeRealtime, refresh, selected?.id, selected?.status])
  useEffect(() => {
    const controller = new AbortController()
    for (const id of trackedIds('REPORT')) void fetchDailyReport(id, controller.signal).then(report => {
      if (controller.signal.aborted || report.reportType !== 'WEEKLY' || report.date !== weekStart(date)) return
      if (!selectedRef.current) select(report)
      else if (selectedRef.current.id === report.id) mergeRealtime(report)
    }).catch(() => undefined)
    return () => controller.abort()
  }, [date, mergeRealtime, select])
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])
  useEffect(() => onSummaryChange?.(dirty ? '有未保存修改' : selected?.status === 'PROCESSING' ? '生成中'
    : selected?.status === 'FAILED' ? '生成失败' : `${total} 个版本`), [dirty, onSummaryChange, selected?.status, total])
  useEffect(() => { if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const confirmDiscard = async () => !dirty || await showDialog({ title: '放弃未保存修改？', description: '周报还有未保存修改，继续操作会放弃这些内容。', confirmLabel: '放弃修改', danger: true })
  const generate = async () => {
    if (!await confirmDiscard()) return
    active.current?.abort(); const controller = new AbortController(); active.current = controller
    setBusy(true); setError(null); setMessage(null)
    try {
      let report = await createWeeklyReport(date, crypto.randomUUID(), controller.signal); select(report); refresh(report.id)
      for (let attempt = 0; report.status === 'PROCESSING' && attempt < 20; attempt += 1) {
        await wait(800, controller.signal); report = await fetchDailyReport(report.id, controller.signal); if (selectedRef.current?.id === report.id) mergeRealtime(report)
      }
      if (report.status === 'PROCESSING') {
        setMessage('周报仍在后台生成，完成后会自动显示。')
        return
      }
      if (selectedRef.current?.id === report.id) refresh(report.id)
      if (report.status === 'FAILED') throw new Error(report.errorMessage ?? '周报生成失败')
      setBusy(false)
      setMessage('周报新版本已生成，来源快照和旧版本均已保留。')
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : '周报生成失败')
    } finally { if (active.current === controller) { active.current = null; setBusy(false) } }
  }
  const saveBody = (event: FormEvent) => {
    event.preventDefault(); if (!selected) return
    const savedDraft = draft
    setBusy(true); setError(null)
    void saveDailyReport(selected.id, draft, selected.version).then((saved) => {
      if (selectedRef.current?.id !== saved.id) return
      if (draftRef.current === savedDraft) bodyDirtyRef.current = false
      mergeRealtime(saved)
      setMessage('AI 正文已保存。')
    })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '保存失败')).finally(() => setBusy(false))
  }
  const saveManual = () => {
    if (!selected) return
    const savedDraft = manualDraft
    setBusy(true); setError(null)
    void saveReportManualAdditions(selected.id, manualDraft, selected.version).then((saved) => {
      if (selectedRef.current?.id !== saved.id) return
      if (manualRef.current === savedDraft) manualDirtyRef.current = false
      mergeRealtime(saved)
      setMessage('用户补充已独立保存，不会显示为 AI 有来源内容。')
    }).catch((caught: unknown) => setError(caught instanceof Error ? caught.message : '保存失败')).finally(() => setBusy(false))
  }
  const copy = async () => {
    const combined = manualDraft.trim() ? `${draft}\n\n## 用户补充（无 AI 来源标记）\n${manualDraft.trim()}` : draft
    try { await navigator.clipboard.writeText(combined); setMessage('已复制周报正文及明确标记的用户补充。') }
    catch { setError('复制失败，请手动选择内容复制') }
  }

  return <article className="panel daily-report weekly-report" data-testid="weekly-report">
    <div className="section-heading"><div><p className="kicker">WEEKLY REPORT</p><h2>自然周汇总</h2><p className="muted">{weekLabel(date)}</p></div><input aria-label="周报所在日期" type="date" value={date} onChange={(event) => { onDateChange(event.target.value) }} /></div>
    <div className="report-toolbar"><button disabled={busy} type="button" onClick={() => void generate()}>{busy ? '生成中…' : '生成周报新版本'}</button><select aria-label="周报历史版本" value={selected?.id ?? ''} onChange={async (event) => { const report = reports.find((item) => item.id === event.target.value); if (report && await confirmDiscard()) selectReport(report.id) }}><option value="">{reports.length ? '选择历史版本' : '暂无历史版本'}</option>{reports.map((report) => <option key={report.id} value={report.id}>{new Date(report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {report.status}</option>)}</select></div>
    <Pagination page={page} totalPages={totalPages} size={size} loading={loading} onPage={async (value) => { if (await confirmDiscard()) setPage(value) }} onSize={async (value) => { if (await confirmDiscard()) setSize(value) }} />
    {error && <p className="inline-notice inline-error" role="alert">{error}</p>}{message && <p className="inline-notice inline-success" role="status">{message}</p>}
    {selected && <><p className="report-meta">{weekLabel(selected.date)} · 版本 {selected.version} · {selected.sourceCount} 个冻结来源{selected.previousReportId ? ' · 基于上一版本重新生成' : ''}</p>
      {selected.status === 'SUCCEEDED' && <><form onSubmit={saveBody}><label>AI 周报正文<textarea data-testid="weekly-content" rows={16} maxLength={20000} value={draft} onChange={(event) => { setDraft(event.target.value); draftRef.current = event.target.value; bodyDirtyRef.current = true }} /></label><div className="actions"><button disabled={busy} type="submit">保存 AI 正文</button><button className="secondary" type="button" onClick={() => void copy()}>复制组合周报</button></div></form><label>用户补充（不会附加 AI 来源）<textarea data-testid="weekly-manual" rows={5} maxLength={20000} value={manualDraft} onChange={(event) => { setManualDraft(event.target.value); manualRef.current = event.target.value; manualDirtyRef.current = true }} /></label><button disabled={busy} type="button" onClick={saveManual}>独立保存用户补充</button></>}
      {selected.status === 'PROCESSING' && <p className="capture-state">来源已冻结，AI 正在生成本版本；折叠面板也不会中断。</p>}{selected.status === 'FAILED' && <p className="inline-notice inline-error">{selected.errorMessage}{selected.errorCode ? ` · ${selected.errorCode} / ${selected.errorStage} · ${selected.sourceCount} 个来源` : ''}</p>}
      {selected.editedAt && <p className="muted">AI 正文已由用户编辑；来源标记不会因人工修改自动重算。</p>}
      <details className="report-sources"><summary>核对本版本来源（{selected.sourceCount}）</summary>{selected.sourceCount === 0 ? <p className="muted">本周期没有可用于周报的有效事实或明确计划。</p> : sources.items.map((source, index) => <article key={source.id}><strong>来源 {sources.page * sources.size + index + 1} · {roleLabel[source.role]} · {source.projectName ?? '未归属项目'}</strong><p>{source.content}</p><small>{new Date(source.sourceTime).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {source.status}</small></article>)}<Pagination page={sources.page} totalPages={sources.pages} size={sources.size} loading={sources.loading} onPage={sources.setPage} onSize={sources.setSize} /></details>
    </>}
  </article>
}
