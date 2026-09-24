import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { createDailyReport, deleteDailyReport } from '../../api/reports'
import { fetchDailyReport, saveDailyReport, type DailyReport } from '../../api/reports'
import { finishTracking, trackEntity, trackedIds } from '../../hooks/realtime'
import { Pagination } from '../../components/Pagination'
import { useReportHistory } from './useReportHistory'
import { useReportSources } from './useReportSources'
import { useDialog } from '../../components/dialogContext'
import { Icon } from '../../components/Icon'
import { useToast } from '../../components/toastContext'

const statusLabel = { PROCESSING: '生成中', SUCCEEDED: '已生成', FAILED: '生成失败' } as const

const wait = (milliseconds: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  const timer = window.setTimeout(resolve, milliseconds)
  signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
})

export function DailyReportPanel({ date, onDateChange, onDirtyChange, onSummaryChange }: {
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
  const draftRef = useRef('')
  const selectedRef = useRef<DailyReport | null>(null)
  const userDirtyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const deletedIds = useRef(new Set<string>())
  const dateRef = useRef(date)
  useEffect(() => { dateRef.current = date }, [date])
  const [sourcesOpen, setSourcesOpen] = useState(true)
  const active = useRef<AbortController | null>(null)
  const sourceReset = useRef<() => void>(() => undefined)
  const dirty = selected?.status === 'SUCCEEDED' && draft !== selected.content
  const select = useCallback((report: DailyReport) => {
    if (deletedIds.current.has(report.id)) return
    setSelected(report); selectedRef.current = report; setDraft(report.content); draftRef.current = report.content; userDirtyRef.current = false
    sourceReset.current()
  }, [])
  const mergeRealtime = useCallback((report: DailyReport) => {
    if (deletedIds.current.has(report.id)) return
    const current = selectedRef.current
    if (current?.id === report.id && (report.version < current.version
        || (current.status !== 'PROCESSING' && report.status === 'PROCESSING'))) return
    const preserve = selectedRef.current?.id === report.id && userDirtyRef.current
    setSelected(report); selectedRef.current = report
    if (!preserve) { setDraft(report.content); draftRef.current = report.content }
  }, [])
  const onHistorySelect = useCallback((report: DailyReport | null) => {
    if (report) {
      if (selectedRef.current?.id === report.id) mergeRealtime(report)
      else select(report)
    } else { setSelected(null); selectedRef.current = null; setDraft(''); draftRef.current = ''; userDirtyRef.current = false }
  }, [mergeRealtime, select])
  const { reports, setReports, page, total, totalPages, loading, setPage, refresh, selectReport } =
    useReportHistory('DAILY', date, onHistorySelect, reportError)
  const sources = useReportSources(selected?.id, selected?.sourceCount, reportError)
  const removeFromView = useCallback((id: string) => {
    deletedIds.current.add(id)
    finishTracking('REPORT', id)
    if (selectedRef.current?.id !== id) return
    active.current?.abort()
    onHistorySelect(null)
    refresh()
  }, [onHistorySelect, refresh])
  useEffect(() => { sourceReset.current = sources.reset })
  useEffect(() => () => { active.current?.abort(); active.current = null }, [])
  useEffect(() => { active.current?.abort() }, [date])
  useEffect(() => {
    const id = selected?.id
    if (!id) return
    const stop = trackEntity('REPORT', id, event => {
      if (event?.state === 'DELETED') { removeFromView(id); return }
      void fetchDailyReport(id).then(report => {
      if (selectedRef.current?.id !== id) return
      mergeRealtime(report)
      if (report.status !== 'PROCESSING') {
        finishTracking('REPORT', id)
        if (selected?.status === 'PROCESSING') refresh(id)
      }
    }).catch((caught: unknown) => {
      if (caught instanceof Error && 'status' in caught && caught.status === 404) removeFromView(id)
    })
    })
    if (selected?.status !== 'PROCESSING') finishTracking('REPORT', id)
    return stop
  }, [mergeRealtime, refresh, removeFromView, selected?.id, selected?.status])
  useEffect(() => {
    const controller = new AbortController()
    for (const id of trackedIds('REPORT')) void fetchDailyReport(id, controller.signal).then(report => {
      if (controller.signal.aborted || report.reportType !== 'DAILY' || report.date !== date) return
      if (!selectedRef.current) select(report)
      else if (selectedRef.current.id === report.id) mergeRealtime(report)
    }).catch((caught: unknown) => {
      if (!controller.signal.aborted && caught instanceof Error && 'status' in caught && caught.status === 404) {
        finishTracking('REPORT', id)
      }
    })
    return () => controller.abort()
  }, [date, mergeRealtime, select])
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])
  useEffect(() => onSummaryChange?.(dirty ? '有未保存修改' : selected?.status === 'PROCESSING' ? '生成中'
    : selected?.status === 'FAILED' ? '生成失败' : `${total} 个版本`), [dirty, onSummaryChange, selected?.status, total])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const confirmDiscard = async () => !dirty || await showDialog({ title: '放弃未保存修改？', description: '日报还有未保存修改，继续操作会放弃这些内容。', confirmLabel: '放弃修改', danger: true })

  const generate = async () => {
    if (!await confirmDiscard()) return
    active.current?.abort(); const controller = new AbortController(); active.current = controller
    setBusy(true)
    try {
      let report = await createDailyReport(date, crypto.randomUUID(), controller.signal); select(report); refresh(report.id)
      for (let attempt = 0; report.status === 'PROCESSING' && attempt < 15; attempt += 1) {
        await wait(800, controller.signal); report = await fetchDailyReport(report.id, controller.signal); if (selectedRef.current?.id === report.id) mergeRealtime(report)
      }
      if (report.status === 'PROCESSING') {
        notify('日报仍在后台生成，完成后会自动显示。')
        return
      }
      if (selectedRef.current?.id === report.id) refresh(report.id)
      if (report.status === 'FAILED') throw new Error(report.errorMessage ?? '日报生成失败')
      setBusy(false)
      notify('日报已生成；修改正文后需要明确保存。', 'success')
    } catch (caught) {
      if (!(caught instanceof DOMException && caught.name === 'AbortError')) notify(caught instanceof Error ? caught.message : '日报生成失败', 'error')
    } finally {
      if (active.current === controller) { active.current = null; setBusy(false) }
    }
  }
  const save = (event: FormEvent) => {
    event.preventDefault(); if (!selected) return
    const savedDraft = draft
    setBusy(true)
    void saveDailyReport(selected.id, draft, selected.version).then((saved) => {
      setReports((items) => items.map((item) => item.id === saved.id ? saved : item))
      if (selectedRef.current?.id !== saved.id) return
      if (draftRef.current === savedDraft) userDirtyRef.current = false
      mergeRealtime(saved); notify('日报正文已保存。', 'success')
    }).catch((caught: unknown) => notify(caught instanceof Error ? caught.message : '保存失败', 'error')).finally(() => setBusy(false))
  }
  const copy = async () => {
    try { await navigator.clipboard.writeText(draft); notify('已复制当前编辑区内容（未自动保存）。', 'success') }
    catch { notify('复制失败，请手动选择正文复制', 'error') }
  }

  const remove = async () => {
    const report = selectedRef.current
    if (!report || report.status === 'PROCESSING' || busy || deleting) return
    const targetDate = date
    await showDialog({ title: '删除此日报版本？',
      description: `仅删除当前日报版本，其他版本、工作记录和待办均保留。${dirty ? '当前未保存的正文修改也会丢弃。' : ''}`,
      confirmLabel: '删除此版本', danger: true, onConfirm: async () => {
        if (dateRef.current !== targetDate || selectedRef.current?.id !== report.id) throw new Error('当前日报已切换，请关闭弹窗后重新操作')
        setDeleting(true)
        try {
          await deleteDailyReport(report.id, report.version)
          deletedIds.current.add(report.id); finishTracking('REPORT', report.id)
          if (dateRef.current !== targetDate || selectedRef.current?.id !== report.id) return
          active.current?.abort(); onHistorySelect(null); sourceReset.current()
          refresh(); notify('此日报版本已删除，工作记录和其他版本已保留。', 'success')
        } catch (caught) {
          if (dateRef.current === targetDate && selectedRef.current?.id === report.id) notify(caught instanceof Error ? caught.message : '删除失败', 'error')
          throw caught
        } finally { setDeleting(false) }
      } })
  }

  return <article ref={toastRef} className="panel daily-report" data-testid="daily-report">
    <div className="report-controls" role="region" aria-label="日报操作" tabIndex={0}>
    <div className="report-heading-row"><div className="report-heading-copy"><p className="kicker">DAILY REPORT</p><h2>日报编辑</h2></div><div className="report-toolbar"><select aria-label="日报历史版本" value={selected?.id ?? ''} onChange={async (event) => { const report = reports.find((item) => item.id === event.target.value); if (report && await confirmDiscard()) selectReport(report.id) }}><option value="">{reports.length ? '选择历史版本' : '暂无历史版本'}</option>{reports.map((report) => <option key={report.id} value={report.id}>{new Date(report.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {statusLabel[report.status]}</option>)}</select><button disabled={busy} type="button" onClick={() => void generate()}>{busy ? '生成中…' : '生成新日报'}</button><input aria-label="日报日期" type="date" value={date} onChange={(event) => { if (event.target.value) onDateChange(event.target.value) }} /></div></div>
    </div>
    <div className={`report-scroll${selected?.status === 'SUCCEEDED' ? ' report-scroll-filled' : ''}`} role="region" aria-label="日报内容" tabIndex={0}>
    {!selected && !loading && <div className="empty report-empty"><span className="empty-icon"><Icon name="report" size={30} /></span><strong>还没有这一天的日报</strong><span>选择日期，生成第一份日报；之后可编辑正文、核对来源。</span></div>}
    {selected && <div className="report-layout"><div className="report-document" role="region" aria-label="日报正文" tabIndex={0}>{dirty && <span className="unsaved-badge">未保存修改</span>}<div className="daily-report-meta-row"><p className="report-meta">{statusLabel[selected.status]} · 版本 {selected.version} · {selected.sourceCount} 个冻结来源{selected.editedAt ? ' · 已人工编辑' : ''}</p><div className="daily-report-actions">
      {selected.status !== 'PROCESSING' && <button className="text-button danger" type="button" disabled={busy || deleting} onClick={() => void remove()}>{deleting ? '删除中…' : '删除此版本'}</button>}
      {selected.status === 'SUCCEEDED' && <><button disabled={busy} type="submit" form="daily-report-form">保存正文</button><button className="secondary" type="button" onClick={() => void copy()}>复制正文</button></>}
    </div></div>
      {selected.status === 'SUCCEEDED' && <form id="daily-report-form" className="daily-editor-card" onSubmit={save}><label>日报正文<textarea data-testid="daily-content" rows={8} maxLength={20000} value={draft} onChange={(event) => { setDraft(event.target.value); draftRef.current = event.target.value; userDirtyRef.current = true }} /></label></form>}
      {selected.status === 'PROCESSING' && <p className="capture-state">来源已冻结，AI 正在生成本版本；切换工作区也不会中断。</p>}{selected.status === 'FAILED' && <p className="inline-notice inline-error">{selected.errorMessage}{selected.errorCode ? ` · ${selected.errorCode} / ${selected.errorStage} · ${selected.sourceCount} 个来源` : ''}</p>}
      {selected.editedAt && <p className="muted">正文已由用户编辑；“来源 N”仅代表生成时的 AI 引用，人工修改内容不自动继承该引用关系。</p>}
      </div><aside className="report-evidence" role="region" aria-label="日报事实来源"><div className="report-sources"><button className="source-toggle" type="button" aria-expanded={sourcesOpen} onClick={() => setSourcesOpen(value => !value)}>核对本版本来源（{selected.sourceCount}）</button>{sourcesOpen && <><div className="source-rows" role="region" aria-label="日报来源数据" tabIndex={0}>{selected.sourceCount === 0 ? <p className="muted">该日期没有有效记录或已安排计划。</p> : sources.items.map((source, index) => <article key={source.id}><strong>来源 {sources.page * sources.size + index + 1} · {source.type === 'RECORD' ? '记录' : '计划'} · {source.projectName ?? '未归属项目'}</strong><p>{source.content}</p><small>{new Date(source.sourceTime).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · {source.status}</small></article>)}</div><Pagination page={sources.page} totalPages={sources.pages} loading={sources.loading} onPage={sources.setPage} /></>}</div></aside>
    </div>}
    </div>
    <Pagination page={page} totalPages={totalPages} loading={loading} onPage={async (value) => { if (await confirmDiscard()) setPage(value) }} />
  </article>
}
