import type { WorkRecord } from '../../api/records'
import { Pagination } from '../../components/Pagination'
import { Icon } from '../../components/Icon'
import { WORKBENCH_TIME_ZONE, localDate } from '../../utils/date'
import { formatDuration } from '../focus/useFocusController'
import { useDialog } from '../../components/dialogContext'

export function RecordsList({ selectedDate, records, recordTotal, recordPage, recordTotalPages,
  setRecordPage, beginEdit, onDelete, loading, error, onRetry, onCreate }: {
  selectedDate: string; records: WorkRecord[]; recordTotal: number; recordPage: number; recordTotalPages: number
  setRecordPage: (page: number) => void
  beginEdit: (record: WorkRecord) => void; onDelete: (id: string) => Promise<void>; loading: boolean
  error?: string | null; onRetry?: () => void; onCreate?: () => void
}) {
  const showDialog = useDialog()
  return <section className="records" aria-labelledby="records-title" data-testid="record-list" aria-busy={loading}>
    <div className="section-heading"><div className="heading-inline"><h2 id="records-title">{selectedDate === localDate() ? '今天的记录' : `${selectedDate} 的记录`}</h2><span className="count-badge">{loading ? '…' : recordTotal}</span></div><span className="field-hint">最新创建在前</span></div>
    <div className="record-rows" role="region" aria-label="记录数据" tabIndex={0}>
    {error ? <div className="empty error-state" role="alert"><Icon name="alert" size={24} /><strong>暂时无法读取记录</strong><span>{error}</span><button className="secondary" type="button" onClick={onRetry}>重新加载</button></div>
      : loading && records.length === 0 ? <div className="loading-state" role="status"><span className="loading-dot" />正在读取工作记录…</div>
      : records.length === 0 ? <div className="empty"><span className="empty-icon"><Icon name="notebook" size={28} /></span><strong>给这一天留下第一条记录</strong><span>一项进展、一个解决的问题，都值得记下来。</span><button className="secondary" type="button" onClick={onCreate}><Icon name="plus" size={15} />开始记录</button></div>
      : <div className="record-list-card">{records.map(record => <article id={`record-${record.id}`} data-testid="record-item" className={`record ${record.source !== 'MANUAL' ? 'record-automatic' : ''}`} key={record.id}>
        <div className="record-time"><span className="timeline-dot" /><time dateTime={record.occurredAt}>{new Date(record.occurredAt).toLocaleTimeString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, hour: '2-digit', minute: '2-digit' })}</time></div>
        <div className="record-body"><div className="record-meta"><span className={`project-tag ${record.project?.status === 'ARCHIVED' ? 'archived' : ''}`}><Icon name="folder" size={12} />{record.project ? `${record.project.name}${record.project.status === 'ARCHIVED' ? '（已归档）' : ''}` : '未归属项目'}</span><span className="source-label">{record.source === 'TASK_COMPLETION' ? <><Icon name="check" size={12} />任务完成</> : record.source === 'FOCUS_SESSION' ? <><Icon name="clock" size={12} />会话计时</> : '工作记录'}</span></div><p>{record.content}</p>{record.focusMs != null && <p className="focus-record-duration">会话净时长 {formatDuration(record.focusMs)} · 微休息 {formatDuration(record.breakMs ?? 0)}。系统不检测实际工作；停工请手动暂停或结束。</p>}{record.source === 'FOCUS_SESSION' ? record.progress && <p className="completion-result">进展：{record.progress}</p> : record.completionResult && <p className="completion-result">完成结果：{record.completionResult}</p>}<time className="record-created" dateTime={record.createdAt}>录入于 {new Date(record.createdAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE })}</time></div>
        {record.source === 'MANUAL' && <div className="record-actions"><button className="text-button" type="button" onClick={() => beginEdit(record)}>编辑</button><button className="text-button danger" type="button" onClick={() => void showDialog({ title: '删除工作记录？', description: record.content, confirmLabel: '确认删除', danger: true, onConfirm: () => onDelete(record.id) })}>删除</button></div>}
      </article>)}</div>}
    </div>
    {!error && <Pagination page={recordPage} totalPages={recordTotalPages} loading={loading} onPage={setRecordPage} />}
  </section>
}
