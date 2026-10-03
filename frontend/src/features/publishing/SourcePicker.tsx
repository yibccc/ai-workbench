import { useCallback, useRef, useState } from 'react'
import { composeShare, fetchMaterials, type Material, type SourceField } from '../../api/publishing'
import { usePagedList } from '../../hooks/usePagedList'
import { useDialog } from '../../components/dialogContext'
import { localDate } from '../../utils/date'
import { Button, CommunityIcon, Empty, Heading, LoadState, Notice, Pager, Stepper } from '../community/ui'
import { errorMessage } from '../community/model'

const silentError = () => undefined
export function SourcePicker({ onCreated }: { onCreated: (postId: string) => void }) {
  const [date, setDate] = useState(localDate(new Date())); const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Map<string, { material: Material; fields: SourceField[] }>>(new Map())
  const [includeFocus, setIncludeFocus] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const pending = useRef(false)
  const query = useCallback((signal: AbortSignal) => fetchMaterials(date, page, signal), [date, page]); const list = usePagedList(query, silentError, setPage); const dialog = useDialog()
  const focused = [...selected.values()].reduce((sum, entry) => sum + (entry.material.focusMs ?? 0), 0)
  const changeDate = async (next: string) => {
    if (!next || date === next) return
    if (selected.size && !await dialog({ className: 'community-dialog', title: '切换素材日期？', description: '切换日期会清空本次已选素材。原始记录不会改变。', confirmLabel: '切换日期' })) return
    setDate(next); setPage(0); setSelected(new Map()); setIncludeFocus(false); setError(null)
  }
  const toggle = (material: Material, checked: boolean) => setSelected(previous => { const next = new Map(previous); if (checked) next.set(material.id, { material, fields: ['CONTENT'] }); else next.delete(material.id); return next })
  const field = (id: string, value: SourceField, checked: boolean) => setSelected(previous => {
    const next = new Map(previous); const entry = next.get(id)
    if (entry) next.set(id, { ...entry, fields: checked ? [...new Set([...entry.fields, value])] : entry.fields.filter(item => item !== value) })
    return next
  })
  const compose = async () => {
    if (pending.current || !selected.size) return
    pending.current = true; setBusy(true); setError(null)
    try { const post = await composeShare({ date, selections: [...selected.entries()].map(([recordId, entry]) => ({ recordId, fields: entry.fields })), includeFocus }); onCreated(post.postId) }
    catch (caught) { setError(errorMessage(caught)) } finally { pending.current = false; setBusy(false) }
  }
  return <><a className="back-link" href="#/community"><CommunityIcon name="arrow-left" />返回广场</a><Heading eyebrow="TODAY, IN YOUR WORDS" title="挑选今天，值得分享的部分" description="先选素材，再整理成稿；不会自动公开整天的记录。" /><Stepper active={0} /><div className="columns"><section className="card"><div className="source-top"><div className="spread"><h2>我的工作记录</h2><label><span className="sr-only">素材日期</span><input className="source-date" type="date" aria-label="素材日期" value={date} disabled={busy} onChange={event => void changeDate(event.target.value)} /></label></div><div className="source-privacy"><Notice>此处是你的私有素材。原始项目、具体时间和未勾选记录不会默认出现在分享稿中。</Notice></div></div><LoadState loading={list.loading} error={list.error} retry={() => void list.refresh()} /><div className="source-list">{!list.loading && !list.error && (list.data?.items.length ? list.data.items.map(material => <div className="source-entry" key={material.id} data-source-id={material.id}><label className="source-row"><input type="checkbox" aria-label={`选择素材 ${material.content}`} checked={selected.has(material.id)} disabled={busy} onChange={event => toggle(material, event.target.checked)} /><div className="source-text"><strong>{material.content}</strong>{material.completionResult && <p>完成结果：{material.completionResult}</p>}{material.progress && <p>专注进展：{material.progress}</p>}<div className="meta"><span className={`badge ${material.focusMs ? 'purple' : ''}`}>{{ MANUAL: '工作记录', TASK_COMPLETION: '任务完成', FOCUS_SESSION: '专注记录' }[material.source]}</span>{material.projectName && <span><CommunityIcon name="archive" />{material.projectName} · 私有项目</span>}{material.focusMs ? <span>专注 {(material.focusMs / 60000).toFixed(1)} 分钟</span> : null}</div></div></label>{selected.has(material.id) && <fieldset className="source-public-fields"><legend>本条公开字段</legend>{([['CONTENT', '记录内容', true], ['COMPLETION_RESULT', '完成结果', !!material.completionResult], ['PROGRESS', '专注进展', !!material.progress]] as const).filter(([, , available]) => available).map(([value, label]) => <label key={value}><input type="checkbox" checked={selected.get(material.id)!.fields.includes(value)} disabled={busy} onChange={event => field(material.id, value, event.target.checked)} />{label}</label>)}</fieldset>}</div>) : <Empty title="这一天还没有记录" description="可以选择其他日期，或先在工作台记录今天的进展。" />)}</div>{list.data && <Pager page={page} totalPages={list.data.totalPages} total={list.data.totalElements} onPage={setPage} loading={list.loading || busy} />}</section><aside className="aside-sticky source-aside"><div className="card side-card"><h3>这次准备分享</h3><div><span className="counter-em">{selected.size}</span><span className="subtle">条已选素材</span></div>{selected.size ? <ul className="selected-list">{[...selected.values()].map(entry => <li key={entry.material.id}><CommunityIcon name="check" /><span>{entry.material.content}</span></li>)}</ul> : <div className="source-preview-empty">还没有勾选素材。<br />只选你愿意让其他成员看到的部分。</div>}{focused > 0 && <label className="check-label"><input type="checkbox" checked={includeFocus} disabled={busy} onChange={event => setIncludeFocus(event.target.checked)} /><span>展示选中素材的专注合计<br /><small>{(focused / 60000).toFixed(1)} 分钟 · 不等于任务完成数</small></span></label>}<div className="rule" /><Button className="full" disabled={!selected.size || busy || [...selected.values()].some(entry => !entry.fields.length)} onClick={() => void compose()}>{busy ? '正在整理…' : '整理成分享草稿'} <CommunityIcon name="arrow-right" /></Button>{error && <Notice kind="error">{error}</Notice>}<div className="hint-box">下一步仍可编辑和删改。生成草稿不会发布，也不会修改原始记录。</div></div><div className="side-caption">素材按上海业务日期展示。<br />选择跨页保留，不把当前页当成全天。</div></aside></div></>
}
