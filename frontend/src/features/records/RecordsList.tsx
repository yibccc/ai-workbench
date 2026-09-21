import { type WorkRecord } from '../../api/records'
import { Pagination } from '../../components/Pagination'
import { WORKBENCH_TIME_ZONE } from '../../utils/date'
import { useDialog } from '../../components/dialogContext'

export function RecordsList({ selectedDate, records, recordTotal, recordPage, recordSize, recordTotalPages,
  setRecordPage, setRecordSize, beginEdit, onDelete, loading }: {
  selectedDate: string; records: WorkRecord[]; recordTotal: number; recordPage: number; recordSize: number; recordTotalPages: number
  setRecordPage: (page: number) => void; setRecordSize: (size: number) => void
  beginEdit: (record: WorkRecord) => void; onDelete: (id: string) => Promise<void>; loading: boolean
}) {
  const showDialog = useDialog()
  return <section className="records" aria-labelledby="records-title" data-testid="record-list">
            <div className="section-heading"><div><p className="kicker">TIMELINE</p><h2 id="records-title">{selectedDate} 的记录</h2></div><span>{recordTotal} 条</span></div>
            {records.length === 0 ? (
              <div className="empty"><strong>这一天还没有记录</strong><span>在上方写下第一条，或切换日期补记历史工作。</span></div>
            ) : records.map((record) => (
              <article id={`record-${record.id}`} data-testid="record-item" className={`record ${record.source === 'TASK_COMPLETION' ? 'record-automatic' : ''}`} key={record.id}>
                <time>{new Date(record.occurredAt).toLocaleTimeString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, hour: '2-digit', minute: '2-digit' })}</time>
                <div className="record-body">
                  <div className="record-meta">
                    {record.project ? <span className={record.project.status === 'ARCHIVED' ? 'archived' : ''}>{record.project.name}{record.project.status === 'ARCHIVED' ? '（已归档）' : ''}</span> : <span>未归属项目</span>}
                    <span>{record.source === 'TASK_COMPLETION' ? '待办自动完成记录' : '手工记录'}</span>
                    <span>录入于 {new Date(record.createdAt).toLocaleString('zh-CN', { timeZone: WORKBENCH_TIME_ZONE })}</span>
                  </div>
                  <p>{record.content}</p>
                  {record.completionResult && <p className="completion-result">完成结果：{record.completionResult}</p>}
                </div>
                {record.source === 'MANUAL' && <div className="record-actions">
                  <button className="text-button" type="button" onClick={() => beginEdit(record)}>编辑</button>
                  <button className="text-button danger" type="button" onClick={() => void showDialog({ title: '删除工作记录？', description: record.content, confirmLabel: '确认删除', danger: true, onConfirm: () => onDelete(record.id) })}>删除</button>
                </div>}
              </article>
            ))}
            <Pagination page={recordPage} totalPages={recordTotalPages} size={recordSize} loading={loading} onPage={setRecordPage} onSize={(value) => { setRecordSize(value); setRecordPage(0) }} />
          </section>
}
