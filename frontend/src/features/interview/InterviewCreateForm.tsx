import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/http'
import { createInterview, deleteJd, getInterview, getJd, parseJd, retryJd, type InterviewCreate, type InterviewDifficulty, type InterviewDirection, type JdAnalysis } from '../../api/interview'
import type { CurrentResume } from '../../api/resume'
import { useDialog } from '../../components/dialogContext'
import { directions, difficultyLabels, directionLabel } from './model'
import { countCodePoints, failureMessage, isAbort, JD_LIMIT } from './text'

type ParseAttempt = { payload: { requestId: string; direction: InterviewDirection; jdText: string }; revision: number }
export function InterviewCreateForm({ resume, active, onCreated, onCancel, onRefreshResume, onDirty }: {
  resume: CurrentResume | null; active: boolean; onCreated: (id: string) => void; onCancel: () => void
  onRefreshResume: () => Promise<void>; onDirty: (value: boolean) => void
}) {
  const dialog = useDialog()
  const [direction, setDirection] = useState<InterviewDirection>('JAVA_BACKEND'); const [difficulty, setDifficulty] = useState<InterviewDifficulty>('MID')
  const [count, setCount] = useState(5); const [useResume, setUseResume] = useState(false); const [resumeVersion, setResumeVersion] = useState<number | null>(null)
  const [jdText, setJdText] = useState(''); const [analysis, setAnalysis] = useState<JdAnalysis | null>(null)
  const [busy, setBusy] = useState(false); const [parsing, setParsing] = useState(false); const [error, setError] = useState<string | null>(null)
  const [parseUnknown, setParseUnknown] = useState(false); const [createUnknown, setCreateUnknown] = useState(false)
  const [cleanupIds, setCleanupIds] = useState<string[]>([]); const [jdNotice, setJdNotice] = useState<string | null>(null)
  const mounted = useRef(false); const lock = useRef(false); const parseLock = useRef(false)
  const formRevision = useRef(0); const analysisRef = useRef(analysis); const parseAttempt = useRef<ParseAttempt | null>(null)
  const createAttempt = useRef<InterviewCreate | null>(null); const retryAttempt = useRef<{ id: string; payload: { requestId: string; expectedVersion: number } } | null>(null)
  const rawRef = useRef({ direction, jdText }); const readOwner = useRef<AbortController | null>(null)
  const chars = countCodePoints(jdText); const invalid = chars > JD_LIMIT
  const validAnalysis = !!analysis && analysis.status === 'SUCCEEDED' && analysis.result?.matched === true && analysis.direction === direction && analysis.jdText === jdText
  const resumeChanged = useResume && (resumeVersion === null || resumeVersion !== resume?.version || !resume.exists)
  const canCreate = !busy && !parsing && !createUnknown && !parseUnknown && !invalid && !resumeChanged && (!jdText || validAnalysis)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; readOwner.current?.abort(); parseAttempt.current = null; createAttempt.current = null } }, [])
  useEffect(() => { onDirty(!!jdText || !!analysis || busy || parsing || createUnknown || parseUnknown); return () => onDirty(false) }, [jdText, analysis, busy, parsing, createUnknown, parseUnknown, onDirty])
  const cleanup = async (id: string) => {
    try { await deleteJd(id); if (mounted.current) setCleanupIds(ids => ids.filter(value => value !== id)) }
    catch (caught) { if (mounted.current && !isAbort(caught) && (!(caught instanceof ApiError) || caught.status !== 404)) setCleanupIds(ids => ids.includes(id) ? ids : [...ids, id]) }
  }
  const invalidate = () => {
    formRevision.current++; readOwner.current?.abort()
    retryAttempt.current = null
    const previous = analysisRef.current; analysisRef.current = null; setAnalysis(null)
    if (previous) void cleanup(previous.id)
    // A late parse response is fenced using its captured revision, including a change back to the old text.
    setJdNotice(previous || parseAttempt.current ? 'JD 已修改，旧解析已失效。请显式重新解析。' : null)
  }
  const setRaw = (value: string) => { if (value !== jdText) invalidate(); rawRef.current = { direction, jdText: value }; setJdText(value) }
  const setSelectedDirection = (value: InterviewDirection) => { if (value !== direction) invalidate(); rawRef.current = { direction: value, jdText }; setDirection(value) }
  useEffect(() => {
    if (!active || !analysis || !['PENDING', 'PROCESSING'].includes(analysis.status)) return
    const controller = new AbortController(); readOwner.current = controller
    const captured = formRevision.current; let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const next = await getJd(analysis.id, controller.signal)
        if (!mounted.current || controller.signal.aborted || captured !== formRevision.current || analysisRef.current?.id !== next.id) return
        analysisRef.current = next; setAnalysis(next)
        if (next.status === 'PENDING' || next.status === 'PROCESSING') timer = setTimeout(() => void poll(), 2_000)
      } catch (caught) { if (!controller.signal.aborted && mounted.current) setError(`JD 状态读取失败：${failureMessage(caught)}。可以重新读取，查询不会重新解析。`) }
    }
    timer = setTimeout(() => void poll(), 1_000)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [active, analysis])
  const parse = async () => {
    if (parseLock.current || lock.current || (!parseAttempt.current && (!jdText || invalid || analysisRef.current))) return
    parseLock.current = true; setParsing(true); setError(null)
    const pending = parseAttempt.current ?? { payload: { direction, jdText, requestId: crypto.randomUUID() }, revision: formRevision.current }
    parseAttempt.current = pending
    try {
      const next = await parseJd(pending.payload)
      if (!mounted.current) return
      parseAttempt.current = null; setParseUnknown(false)
      if (pending.revision !== formRevision.current || next.direction !== rawRef.current.direction || next.jdText !== rawRef.current.jdText) { void cleanup(next.id); setJdNotice('旧 JD 解析结果已丢弃，请解析当前文本。'); return }
      analysisRef.current = next; setAnalysis(next); setJdNotice(null)
    } catch (caught) {
      if (!mounted.current || isAbort(caught)) return
      if (caught instanceof ApiError && caught.status < 500) { parseAttempt.current = null; setParseUnknown(false); setError(failureMessage(caught)) }
      else { setParseUnknown(true); setError('JD 解析响应未确认。请用原请求确认结果；当前正文保留，不会按无 JD 创建。') }
    } finally { parseLock.current = false; if (mounted.current) setParsing(false) }
  }
  const retry = async () => {
    const previous = analysisRef.current
    if (!previous || previous.status !== 'FAILED' || parseLock.current) return
    parseLock.current = true; setParsing(true); setError(null)
    const captured = formRevision.current
    const pending = retryAttempt.current ?? { id: previous.id, payload: { expectedVersion: previous.version, requestId: crypto.randomUUID() } }; retryAttempt.current = pending
    try {
      const next = await retryJd(pending.id, pending.payload)
      if (!mounted.current) return
      retryAttempt.current = null
      if (captured !== formRevision.current || analysisRef.current?.id !== previous.id) { void cleanup(next.id); return }
      analysisRef.current = next; setAnalysis(next)
    } catch (caught) { if (mounted.current && !isAbort(caught)) { if (caught instanceof ApiError && caught.status < 500) retryAttempt.current = null; setError(failureMessage(caught)) } }
    finally { parseLock.current = false; if (mounted.current) setParsing(false) }
  }
  const create = async () => {
    if (lock.current || (!createAttempt.current && !canCreate)) return
    lock.current = true; setBusy(true); setError(null)
    const pending = createAttempt.current ?? { requestId: crypto.randomUUID(), direction, difficulty, mainQuestionCount: count, useCurrentResume: useResume, expectedResumeVersion: useResume ? resumeVersion : null, jdText: jdText || null, jdAnalysisId: jdText ? analysisRef.current?.id ?? null : null }
    createAttempt.current = pending
    try {
      const receipt = await createInterview(pending)
      if (!mounted.current) return
      // A replayed receipt is not the current projection (the session may already be deleted).
      const session = await getInterview(receipt.sessionId)
      if (!mounted.current) return
      createAttempt.current = null; setCreateUnknown(false); onCreated(session.id)
    } catch (caught) {
      if (!mounted.current || isAbort(caught)) return
      if (caught instanceof ApiError && caught.status < 500) {
        createAttempt.current = null; setCreateUnknown(false)
        if (caught.status === 409) { await onRefreshResume(); setError('创建配置或当前简历版本发生冲突。请确认当前生效版本后再创建。') }
        else setError(failureMessage(caught))
      } else { setCreateUnknown(true); setError('创建响应未确认。原配置和请求标识已保留，点击确认结果将重放原请求，不会盲建第二场。') }
    } finally { lock.current = false; if (mounted.current) setBusy(false) }
  }
  const refreshAnalysis = async () => {
    const previous = analysisRef.current; if (!previous) return
    const captured = formRevision.current
    try {
      const next = await getJd(previous.id)
      if (!mounted.current || captured !== formRevision.current || analysisRef.current?.id !== next.id) return
      analysisRef.current = next; setAnalysis(next); setError(null)
      if (retryAttempt.current && next.version > retryAttempt.current.payload.expectedVersion) retryAttempt.current = null
    } catch (caught) { if (mounted.current && !isAbort(caught)) setError(failureMessage(caught)) }
  }
  const removeJd = async () => {
    if (busy || parsing || parseUnknown || createUnknown) return
    if (jdText && !await dialog({ title: '移除 JD？', description: '将清空当前职位描述与解析；之后可创建无 JD 面试。已创建面试的快照保持不变。', confirmLabel: '移除 JD' })) return
    invalidate(); rawRef.current = { direction, jdText: '' }; setJdText(''); setJdNotice(null)
  }
  const cancel = async () => {
    if (busy || parsing || createUnknown || parseUnknown) { setError('请先确认正在处理的请求。'); return }
    if ((jdText || analysis) && !await dialog({ title: '取消创建草稿？', description: '将清空 JD 与独立解析。已有面试不会受到影响。', confirmLabel: '取消创建', danger: true })) return
    invalidate(); setJdText(''); rawRef.current = { direction, jdText: '' }; onCancel()
  }
  return <form className="card form-card" onSubmit={event => { event.preventDefault(); void create() }} aria-busy={busy}>
    {error && <div className="note danger-note" role="alert">{error}</div>}
    {cleanupIds.length > 0 && <div className="note warning-note" role="alert">旧解析已失效，但 {cleanupIds.length} 项清理尚未确认。<button className="text-button" type="button" onClick={() => cleanupIds.forEach(id => void cleanup(id))}>确认清理</button></div>}
    <fieldset className="form-section" disabled={busy || createUnknown}><legend>1. 选择方向</legend><div className="option-grid">{directions.map(item => <label key={item.value} className={`option ${direction === item.value ? 'active' : ''}`}><input type="radio" name="interview-direction" value={item.value} checked={direction === item.value} onChange={() => setSelectedDirection(item.value)} /><strong>{item.label}</strong><small>{item.description}</small></label>)}</div></fieldset>
    <fieldset className="form-section" disabled={busy || createUnknown}><legend>2. 面试设置</legend><div className="row"><label>难度<select value={difficulty} onChange={event => setDifficulty(event.target.value as InterviewDifficulty)}>{(Object.entries(difficultyLabels) as [InterviewDifficulty, string][]).map(([value, label]) => <option key={value} value={value}>{label}{value === 'MID' ? '（推荐）' : ''}</option>)}</select></label><label>主问题数量<select value={count} onChange={event => setCount(Number(event.target.value))}>{Array.from({ length: 18 }, (_, index) => index + 3).map(value => <option key={value} value={value}>{value}{value === 5 ? '（默认）' : ''}</option>)}</select></label></div><div className="note">{count} 道主问题 + 每题固定 1 道预生成追问 = <strong>{count * 2} 轮作答</strong>。每轮提交成功后不可回改。</div></fieldset>
    <fieldset className="form-section" disabled={busy || createUnknown}><legend>3. 简历</legend><div className="resume-choice"><label className={`resume-box ${useResume ? 'active' : ''}`}><input type="radio" name="resume-choice" disabled={!resume?.exists} checked={useResume} onChange={() => { setUseResume(true); setResumeVersion(resume?.version ?? null) }} /><strong>使用当前简历</strong><span>{resume?.exists ? `${resume.originalFile?.fileName ?? '粘贴文本'} · ${countCodePoints(resume.markdownText ?? '')} 字符 · 版本 ${resume.version}` : '无当前简历'}<br />创建成功后保存本场不可变文本快照。</span></label><label className={`resume-box ${!useResume ? 'active' : ''}`}><input type="radio" name="resume-choice" checked={!useResume} onChange={() => { setUseResume(false); setResumeVersion(null) }} /><strong>无简历通用面试</strong><span>只按方向、难度和可选 JD 出题。</span></label></div><p className="tiny muted">面试页不能上传其他简历。需要更换时请先到<a href="#profile">个人中心</a>更新当前简历。</p>{resumeChanged && <div className="note warning-note" role="alert">生效简历版本已变化，请确认当前版本。<button type="button" className="secondary" disabled={!resume?.exists} onClick={() => setResumeVersion(resume?.version ?? null)}>确认使用版本 {resume?.version}</button></div>}</fieldset>
    <section className="form-section"><h2>4. 可选 JD</h2><label>职位描述<textarea rows={6} placeholder="粘贴目标岗位的职位描述；最多 10,000 字符" value={jdText} onChange={event => setRaw(event.target.value)} disabled={busy || createUnknown} aria-invalid={invalid} /></label><div className="jd-actions"><span className={invalid ? 'input-error' : 'tiny muted'}>{chars.toLocaleString('en-US')} / 10,000 字符</span><div className="action-group"><button type="button" className="secondary" disabled={busy || parsing || (!parseUnknown && (!!analysis || !jdText || invalid))} onClick={() => void parse()}>{parsing ? '正在解析…' : parseUnknown ? '确认解析结果' : '解析 JD'}</button><button type="button" className="text-button" disabled={busy || parsing || parseUnknown || createUnknown} onClick={() => void removeJd()}>移除 JD</button></div></div>
      {invalid && <p className="input-error" role="alert">JD 超过 10,000 字符。全文保留，请修改后解析。</p>}{jdNotice && <p className="tiny muted" role="status">{jdNotice}</p>}
      {analysis && <div className="jd-result"><button type="button" className="text-button" disabled={busy || parsing} onClick={() => void refreshAnalysis()}>重新读取 JD 状态</button><div className="session-top"><strong>解析结果 · {directionLabel(analysis.direction)}</strong><span className={`status ${analysis.status === 'SUCCEEDED' && validAnalysis ? 'done' : analysis.status === 'FAILED' || analysis.result?.matched === false ? 'failed' : 'waiting'}`}>{analysis.status === 'PENDING' ? '等待解析' : analysis.status === 'PROCESSING' ? '正在解析' : analysis.status === 'FAILED' ? '解析失败' : validAnalysis ? '已解析' : '方向不匹配'}</span></div>{analysis.result && <><p>{analysis.result.summary}</p><div className="chips">{analysis.result.focusPoints.map((point, index) => <span className="chip" key={index}>{point}</span>)}</div></>}{analysis.status === 'FAILED' && <><p role="alert">{analysis.safeFailureCode ?? '解析未完成'}；原文保留，不能带失败分析创建。</p><button type="button" className="secondary" disabled={parsing || busy} onClick={() => void retry()}>手动重试 JD 解析</button></>}<p className="tiny muted">修改 JD 任一字符或方向后，该解析结果立即失效。成功解析可用于同配置的多场面试。</p></div>}
    </section>
    <div className="form-actions"><button type="button" className="secondary" disabled={busy} onClick={() => void cancel()}>取消</button><button type="submit" disabled={busy || (!createUnknown && !canCreate)}>{busy ? '正在创建…' : createUnknown ? '确认创建结果' : `创建面试 · ${count * 2} 轮`}</button></div>
  </form>
}
