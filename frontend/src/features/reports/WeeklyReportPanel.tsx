import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import {
  createWeeklyReport } from '../../api/reports'
import { fetchDailyReport, saveDailyReport, saveReportManualAdditions, type DailyReport, type ReportSourceRole } from '../../api/reports'
import { finishTracking, trackEntity, trackedIds } from '../../hooks/realtime'
import { Pagination } from '../../components/Pagination'
import { useReportHistory } from './useReportHistory'
import { useReportSources } from './useReportSources'
import { ReportSourceDetails } from './ReportSourceDetails'
import { useDialog } from '../../components/dialogContext'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/toastContext'

const statusLabel = { PROCESSING: '生成中', SUCCEEDED: '已生成', FAILED: '生成失败' } as const

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
  const { notify, toastRef } = useToast<HTMLElement>()
  const reportError = useCallback((message: string | null) => { if (message) notify(message, 'error') }, [notify])
  const [selected, setSelected] = useState<DailyReport | null>(null)
  const [draft, setDraft] = useState('')
  const [manualDraft, setManualDraft] = useState('')
  const draftRef = useRef('')
  const manualRef = useRef('')
  const selectedRef = useRef<DailyReport | null>(null)
  const bodyDirtyRef = useRef(false)
  const manualDirtyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [sourcesOpen, setSourcesOpen] = useState(true)
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
  const { reports, page, total, totalPages, loading, setPage, refresh, selectReport } =
    useReportHistory('WEEKLY', date, onHistorySelect, reportError)
  const sources = useReportSources(selected?.id, selected?.sourceCount, reportError)
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
    setBusy(true)
    try {
      let report = await createWeeklyReport(date, crypto.randomUUID(), controller.signal); select(report); refresh(report.id)
      for (let attempt = 0; report.status === 'PROCESSING' && attempt < 20; attempt += 1) {
        await wait(800, controller.signal); report = await fetchDailyReport(report.id, controller.signal); if (selectedRef.current?.id === report.id) mergeRealtime(report)
      }
      if (report.status === 'PROCESSING') {
        notify('周报仍在后台生成，完成后会自动显示。')
        return
      }
      if (selectedRef.current?.id === report.id) refresh(report.id)
      if (report.status === 'FAILED') throw new Error(report.errorMessage ?? '周报生成失败')
      setBusy(false)
      notify('周报新版本已生成，来源快照和旧版本均已保留。', 'success')
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) notify(caught instanceof Error ? caught.message : '周报生成失败', 'error')
    } finally { if (active.current === controller) { active.current = null; setBusy(false) } }
  }
  const saveBody = (event: FormEvent) => {
    event.preventDefault(); if (!selected) return
    const savedDraft = draft
    setBusy(true)
    void saveDailyReport(selected.id, draft, selected.version).then((saved) => {
      if (selectedRef.current?.id !== saved.id) return
      if (draftRef.current === savedDraft) bodyDirtyRef.current = false
      mergeRealtime(saved)
      notify('AI 正文已保存。', 'success')
    })
      .catch((caught: unknown) => notify(caught instanceof Error ? caught.message : '保存失败', 'error')).finally(() => setBusy(false))
  }
  const saveManual = () => {
    if (!selected) return
    const savedDraft = manualDraft
    setBusy(true)
    void saveReportManualAdditions(selected.id, manualDraft, selected.version).then((saved) => {
      if (selectedRef.current?.id !== saved.id) return
      if (manualRef.current === savedDraft) manualDirtyRef.current = false
      mergeRealtime(saved)
      notify('用户补充已独立保存，不会显示为 AI 有来源内容。', 'success')
    }).catch((caught: unknown) => notify(caught instanceof Error ? caught.message : '保存失败', 'error')).finally(() => setBusy(false))
  }
  const copy = async () => {
    const combined = manualDraft.trim() ? `${draft}\n\n## 用户补充（无 AI 来源标记）\n${manualDraft.trim()}` : draft
    try { await navigator.clipboard.writeText(combined); notify('已复制周报正文及明确标记的用户补充。', 'success') }
    catch { notify('复制失败，请手动选择内容复制', 'error') }
  }

  return <article ref={toastRef} className="panel daily-report weekly-report" data-testid="weekly-report">
    <div className="report-controls" role="region" aria-label="周报操作" tabIndex={0}>
    <div className="report-heading-row"><div className="report-heading-copy"><p className="kicker">WEEKLY REPORT</p><h2>周报编辑</h2><p className="muted">{weekLabel(date)}</p></div><div className="report-toolbar"><select aria-label="周报历史版本" value={selected?.id ?? ''} onChange={async (event) => { const report = reports.find((item) => item.id === event.target.value); if (report && await confirmDiscard()) selectReport(report.id) }}><option value="">{reports.length ? '选择历史版本' : '暂无历史版本'}</option>{reports.map((report) => <option key={report.id} value={report.id}>{new Date(report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {statusLabel[report.status]}</option>)}</select><button disabled={busy} type="button" onClick={() => void generate()}>{busy ? '生成中…' : '生成新周报'}</button><input aria-label="周报所在日期" type="date" value={date} onChange={(event) => { if (event.target.value) onDateChange(event.target.value) }} /></div></div>
    </div>
    <div className={`report-scroll${selected?.status === 'SUCCEEDED' ? ' report-scroll-filled' : ''}`} role="region" aria-label="周报内容" tabIndex={0}>
    {!selected && !loading && <div className="empty report-empty"><span className="empty-icon"><Icon name="report" size={30} /></span><strong>还没有这一周的周报</strong><span>选择日期，生成第一份周报；之后可编辑正文、核对来源。</span></div>}
    {selected && <div className="report-layout"><div className="report-document" role="region" aria-label="周报正文" tabIndex={0}>{dirty && <span className="unsaved-badge">未保存修改</span>}<p className="report-meta">{weekLabel(selected.date)} · 版本 {selected.version} · {selected.sourceCount} 个冻结来源{selected.previousReportId ? ' · 基于上一版本重新生成' : ''}</p>
      {selected.status === 'SUCCEEDED' && <div className="weekly-editor-grid">
        <form id="weekly-ai-form" className="weekly-ai-card" onSubmit={saveBody}><label>AI 周报正文<textarea data-testid="weekly-content" rows={8} maxLength={20000} value={draft} onChange={(event) => { setDraft(event.target.value); draftRef.current = event.target.value; bodyDirtyRef.current = true }} /></label></form>
        <div className="weekly-manual-card">
          <div className="weekly-manual-actions"><button disabled={busy} type="submit" form="weekly-ai-form">保存 AI 正文</button><button className="secondary" type="button" onClick={() => void copy()}>复制组合周报</button></div>
          <label>用户补充（不会附加 AI 来源）<textarea data-testid="weekly-manual" rows={5} maxLength={20000} value={manualDraft} onChange={(event) => { setManualDraft(event.target.value); manualRef.current = event.target.value; manualDirtyRef.current = true }} /></label>
          <button className="weekly-save-manual" disabled={busy} type="button" onClick={saveManual}>独立保存用户补充</button>
        </div>
      </div>}
      {selected.status === 'PROCESSING' && <p className="capture-state">来源已冻结，AI 正在生成本版本；切换工作区也不会中断。</p>}{selected.status === 'FAILED' && <p className="inline-notice inline-error">{selected.errorMessage}{selected.errorCode ? ` · ${selected.errorCode} / ${selected.errorStage} · ${selected.sourceCount} 个来源` : ''}</p>}
      {selected.editedAt && <p className="muted">AI 正文已由用户编辑；来源标记不会因人工修改自动重算。</p>}
      </div><aside className="report-evidence" role="region" aria-label="周报事实来源"><div className="report-sources"><button className="source-toggle" type="button" aria-expanded={sourcesOpen} onClick={() => setSourcesOpen(value => !value)}>核对本版本来源（{selected.sourceCount}）</button>{sourcesOpen && <><div className="source-rows" role="region" aria-label="周报来源数据" tabIndex={0}>{selected.sourceCount === 0 ? <p className="muted">本周期没有可用于周报的有效事实或明确计划。</p> : sources.items.map((source, index) => <article key={source.id}><strong>来源 {sources.page * sources.size + index + 1} · {source.status === 'FOCUS_SESSION' ? '专注投入' : roleLabel[source.role]} · {source.projectName ?? '未归属项目'}</strong><p>{source.content}</p><ReportSourceDetails source={source} /><small>{new Date(source.sourceTime).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {source.status}</small></article>)}</div><Pagination page={sources.page} totalPages={sources.pages} loading={sources.loading} onPage={sources.setPage} /></>}</div></aside>
    </div>}
    </div>
    <Pagination page={page} totalPages={totalPages} loading={loading} onPage={async (value) => { if (await confirmDiscard()) setPage(value) }} />
  </article>
}
