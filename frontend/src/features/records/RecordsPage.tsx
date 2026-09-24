import { useCallback, useState } from 'react'
import type { Project } from '../../api/projects'
import { fetchRecord, fetchRecordPage, deleteRecord, type WorkRecord } from '../../api/records'
import type { CaptureInput } from '../../api/capture'
import { WORKSPACE_PAGE_SIZE } from '../../api/pagination'
import { Icon } from '../../components/Icon'
import { SegmentedControl } from '../../components/SegmentedControl'
import { RetainedView } from '../../components/RetainedView'
import { usePagedList } from '../../hooks/usePagedList'
import { localDate } from '../../utils/date'
import { AiCapturePanel } from '../capture/AiCapturePanel'
import { RecordForm, RecordEditorDialog } from './RecordForm'
import { RecordsList } from './RecordsList'
import { useToast } from '../../components/toastContext'

const shiftDate = (date: string, days: number) => {
  const value = new Date(`${date}T12:00:00+08:00`)
  value.setUTCDate(value.getUTCDate() + days)
  return localDate(value)
}
const ignoreListError = () => undefined

export function RecordsPage({ projects, revision, onDataChanged, onEditTask }: {
  projects: Project[]; revision: number; onDataChanged: () => Promise<void>; onEditTask: (id: string) => void
}) {
  const { notify, toastRef } = useToast<HTMLDivElement>()
  const [date, setDate] = useState(localDate)
  const [mode, setMode] = useState<'ai' | 'manual'>('ai')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<WorkRecord | null>(null)
  const query = useCallback((signal: AbortSignal) => fetchRecordPage(date, page, WORKSPACE_PAGE_SIZE, signal), [date, page])
  const list = usePagedList(query, ignoreListError, setPage, revision)
  const changeDate = (value: string) => { if (value) { setDate(value); setPage(0) } }
  const onSaved = async (record: WorkRecord) => { changeDate(localDate(new Date(record.occurredAt))); setPage(0); await onDataChanged() }
  const onGenerated = useCallback(async (result: CaptureInput) => {
    if (result.status === 'SUCCEEDED' && result.records[0]) setDate(localDate(new Date(result.records[0].occurredAt)))
    setPage(0)
    await onDataChanged()
  }, [onDataChanged])
  const focusCapture = () => document.querySelector<HTMLTextAreaElement>(mode === 'ai' ? '[data-testid="capture-content"]' : '[data-testid="record-content"]')?.focus()
  return <div ref={toastRef} className="page records-page">
    <header className="page-header"><div><p className="page-eyebrow">让进展留下痕迹</p><h1>工作记录</h1><p className="page-description">记下已完成的事，也安排好下一步。</p></div><div className="date-navigation" role="group" aria-label="记录日期导航"><button className="icon-button secondary" aria-label="前一天" type="button" onClick={() => changeDate(shiftDate(date, -1))}><Icon name="chevron-left" size={16} /></button><input aria-label="查看日期" type="date" value={date} onChange={event => changeDate(event.target.value)} /><button className="icon-button secondary" aria-label="后一天" type="button" onClick={() => changeDate(shiftDate(date, 1))}><Icon name="chevron-right" size={16} /></button><button className="secondary today-button" type="button" disabled={date === localDate()} onClick={() => changeDate(localDate())}>今天</button></div></header>
    <div className="workspace-scroll" role="region" aria-label="工作记录内容" tabIndex={0}>
    <section className="capture-card" aria-label="快速记录">
      <div className="capture-card-top"><SegmentedControl label="记录方式" value={mode} onChange={setMode} options={[{ value: 'ai', label: <><Icon name="sparkles" size={16} />AI 快记</> }, { value: 'manual', label: <><Icon name="edit" size={16} />手工记录</> }]} /><span className="subtle-label">想到就记，不用先整理</span></div>
      <RetainedView active={mode === 'ai'}><AiCapturePanel onGenerated={onGenerated} onEditTask={onEditTask} onEditRecord={id => {
        void fetchRecord(id).then(setEditing).catch((caught: unknown) => notify(caught instanceof Error ? caught.message : '读取记录失败', 'error'))
      }} /></RetainedView>
      <RetainedView active={mode === 'manual'}><div className="manual-capture"><h2>记一笔工作</h2><p className="field-hint">项目与发生时间可单独指定，保存后会切换到对应日期。</p><RecordForm projects={projects} date={date} onSaved={onSaved} /></div></RetainedView>
    </section>
    <RecordsList selectedDate={date} records={list.data?.items ?? []} recordTotal={list.data?.totalElements ?? 0} recordPage={page} recordTotalPages={list.data?.totalPages ?? 0} loading={list.loading} error={list.error}
      onRetry={() => void list.refresh()} onCreate={focusCapture} setRecordPage={setPage} beginEdit={setEditing}
      onDelete={async id => { await deleteRecord(id); await onDataChanged() }} />
    <div className="workflow-footer"><span>把记录变成一份清晰的汇报</span><a href="#reports">整理日报 / 周报 <Icon name="arrow-right" size={15} /></a></div>
    </div>
    {editing && <RecordEditorDialog key={editing.id} record={editing} projects={projects} date={date} onSaved={onSaved} onClose={() => setEditing(null)} />}
  </div>
}
