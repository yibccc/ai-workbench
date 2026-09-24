import { useEffect, useState } from 'react'
import { RetainedView } from '../../components/RetainedView'
import { SegmentedControl } from '../../components/SegmentedControl'
import { useDialog } from '../../components/dialogContext'
import { localDate } from '../../utils/date'
import { DailyReportPanel } from './DailyReportPanel'
import { WeeklyReportPanel } from './WeeklyReportPanel'

export function ReportsPage({ onDirtyChange }: { onDirtyChange: (dirty: boolean) => void }) {
  const showDialog = useDialog()
  const [type, setType] = useState<'daily' | 'weekly'>('daily')
  const [dailyDate, setDailyDate] = useState(localDate)
  const [weeklyDate, setWeeklyDate] = useState(localDate)
  const [dailyDirty, setDailyDirty] = useState(false)
  const [weeklyDirty, setWeeklyDirty] = useState(false)
  useEffect(() => { onDirtyChange(dailyDirty || weeklyDirty) }, [dailyDirty, weeklyDirty, onDirtyChange])
  const changeDate = async (value: string, reportType: 'daily' | 'weekly') => {
    if (!value) return
    const dirty = reportType === 'daily' ? dailyDirty : weeklyDirty
    if (!dirty || await showDialog({ title: '放弃未保存修改？', description: '切换日期会丢弃当前报告中未保存的内容。', confirmLabel: '放弃修改', danger: true })) {
      if (reportType === 'daily') setDailyDate(value)
      else setWeeklyDate(value)
    }
  }
  return <div className="page reports-page">
    <header className="page-header"><div><p className="page-eyebrow">从零散进展，到清晰汇报</p><h1>工作汇报</h1><p className="page-description">按日或按周整理工作，保留历史版本与生成时的来源。</p></div><div className="report-type-switch"><SegmentedControl label="报告类型" value={type} onChange={setType} options={[{ value: 'daily', label: <>日报{dailyDirty && <span className="dirty-dot" aria-label="有未保存修改" />}</> }, { value: 'weekly', label: <>周报{weeklyDirty && <span className="dirty-dot" aria-label="有未保存修改" />}</> }]} /></div></header>
    {(dailyDirty || weeklyDirty) && <p className="draft-notice" role="status">有未保存修改。切换工作区会保留草稿，刷新或关闭页面前请先保存。</p>}
    <RetainedView active={type === 'daily'}><DailyReportPanel date={dailyDate} onDateChange={value => void changeDate(value, 'daily')} onDirtyChange={setDailyDirty} /></RetainedView>
    <RetainedView active={type === 'weekly'}><WeeklyReportPanel date={weeklyDate} onDateChange={value => void changeDate(value, 'weekly')} onDirtyChange={setWeeklyDirty} /></RetainedView>
  </div>
}
