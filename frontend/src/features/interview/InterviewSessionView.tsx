import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/http'
import { completeInterview, getInterview, getInterviewReport, retryEvaluation, retryGeneration, saveAnswer, submitAnswer, type InterviewReport, type InterviewSession, type VersionOperation } from '../../api/interview'
import { useDialog } from '../../components/dialogContext'
import type { NavigationGuard } from '../../components/layout/navigationGuard'
import { SafeMarkdown } from '../../components/SafeMarkdown'
import { useToast } from '../../components/toastContext'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { InterviewReport as ReportView } from './InterviewReport'
import { sessionStatus, sessionTitle } from './model'
import { ANSWER_LIMIT, countCodePoints, failureMessage, isAbort } from './text'

type Attempt = { operation: 'DRAFT' | 'SUBMIT' | 'COMPLETE' | 'GENERATION' | 'EVALUATION'; payload: VersionOperation; turn: number; text: string; revision: number; leave: boolean }
type ConfirmedWrite = { operation: Attempt['operation']; version: number }
export function InterviewSessionView({ id, active, onBack, onDelete, onChanged, onRead, registerLocalGuard }: {
  id: string; active: boolean; onBack: () => void; onDelete: (id: string) => Promise<void>; onChanged: () => void; onRead: (session: InterviewSession) => void; registerLocalGuard: (id: string, guard: NavigationGuard | null) => void
}) {
  const dialog = useDialog(); const { notify, toastRef } = useToast<HTMLDivElement>()
  const [session, setSession] = useState<InterviewSession | null>(null); const [report, setReport] = useState<InterviewReport | null>(null)
  const [text, setText] = useState(''); const [saved, setSaved] = useState(''); const [editorTurn, setEditorTurn] = useState<number | null>(null)
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [unknown, setUnknown] = useState(false)
  const [confirmedWrite, setConfirmedWrite] = useState<ConfirmedWrite | null>(null)
  const [error, setError] = useState<string | null>(null); const [conflictText, setConflictText] = useState<string | null>(null); const [missing, setMissing] = useState(false)
  const mounted = useRef(false); const lock = useRef(false); const owner = useRef<AbortController | null>(null)
  const sessionRef = useRef<InterviewSession | null>(null); const textRef = useRef(''); const savedRef = useRef(''); const turnRef = useRef<number | null>(null)
  const revision = useRef(0); const attempt = useRef<Attempt | null>(null); const activeRef = useRef(active)
  const confirmedWriteRef = useRef<ConfirmedWrite | null>(null)
  const readGeneration = useRef(0)
  const dirty = text !== saved; const chars = countCodePoints(text); const invalid = chars > ANSWER_LIMIT
  const editable = session?.generationStatus === 'SUCCEEDED' && session.answerStatus !== 'COMPLETED' && editorTurn === session.currentTurn && confirmedWrite?.operation !== 'SUBMIT' && confirmedWrite?.operation !== 'COMPLETE'
  useBeforeUnload(dirty || unknown || busy)
  useEffect(() => { activeRef.current = active }, [active])
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; owner.current?.abort(); attempt.current = null; registerLocalGuard(id, null) } }, [id, registerLocalGuard])
  const clearMissing = useCallback(() => {
    sessionRef.current = null; textRef.current = ''; savedRef.current = ''; turnRef.current = null
    attempt.current = null; confirmedWriteRef.current = null
    setMissing(true); setSession(null); setReport(null); setText(''); setSaved(''); setEditorTurn(null)
    setConflictText(null); setUnknown(false); setConfirmedWrite(null)
  }, [])
  const adopt = useCallback((next: InterviewSession, acknowledge?: Attempt) => {
    if ((sessionRef.current && next.version < sessionRef.current.version) || (confirmedWriteRef.current && next.version < confirmedWriteRef.current.version)) return false
    confirmedWriteRef.current = null; setConfirmedWrite(null)
    sessionRef.current = next; setSession(next); setMissing(false)
    onRead(next)
    const newTurn = next.currentTurn; const answer = next.answers.find(value => value.turnIndex === newTurn)
    const value = answer?.answerText ?? ''
    const changedTurn = turnRef.current !== newTurn
    const pristine = textRef.current === savedRef.current
    const confirmed = acknowledge && acknowledge.revision === revision.current && acknowledge.turn === turnRef.current
    if (changedTurn && !pristine && !confirmed && turnRef.current !== null) setConflictText(textRef.current)
    if (changedTurn || pristine || confirmed) {
      turnRef.current = newTurn; textRef.current = value; savedRef.current = value
      setEditorTurn(newTurn); setText(value); setSaved(value)
    } else if (acknowledge?.operation === 'DRAFT' && acknowledge.turn === newTurn) {
      savedRef.current = acknowledge.text; setSaved(acknowledge.text)
    }
    return true
  }, [onRead])
  const read = useCallback(async (signal?: AbortSignal, acknowledge?: Attempt) => {
    const generation = ++readGeneration.current
    let next: InterviewSession
    try { next = await getInterview(id, signal) }
    catch (caught) {
      if (mounted.current && activeRef.current && !signal?.aborted && generation === readGeneration.current && caught instanceof ApiError && caught.status === 404) clearMissing()
      throw caught
    }
    if (!mounted.current || !activeRef.current || signal?.aborted || generation !== readGeneration.current) return
    if (next.answerStatus === 'COMPLETED') {
      try {
        const result = await getInterviewReport(id, signal)
        if (mounted.current && activeRef.current && !signal?.aborted && generation === readGeneration.current) setReport(result)
      } catch (caught) {
        if (mounted.current && activeRef.current && !signal?.aborted && generation === readGeneration.current) {
          if (caught instanceof ApiError && caught.status === 404) clearMissing()
          else adopt(next, acknowledge)
        }
        throw caught
      }
    }
    if (!mounted.current || !activeRef.current || signal?.aborted || generation !== readGeneration.current) return
    if (!adopt(next, acknowledge)) return
    setLoading(false)
    return next
  }, [id, adopt, clearMissing])
  const refresh = useCallback(async () => {
    owner.current?.abort(); const controller = new AbortController(); owner.current = controller
    setLoading(true); setError(null)
    try { await read(controller.signal) }
    catch (caught) {
      if (!mounted.current || isAbort(caught)) return
      setError(failureMessage(caught))
    } finally { if (mounted.current && owner.current === controller) setLoading(false) }
  }, [read])
  useEffect(() => { let cancelled = false; if (active) void Promise.resolve().then(() => { if (!cancelled) void refresh() }); else { owner.current?.abort(); readGeneration.current++ }; return () => { cancelled = true; owner.current?.abort() } }, [active, refresh])
  const pendingStatus = !!session && (session.generationStatus === 'PENDING' || session.generationStatus === 'PROCESSING' || session.evaluationStatus === 'PENDING' || session.evaluationStatus === 'PROCESSING')
  useEffect(() => {
    if (!active || !pendingStatus) return
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try { await read(controller.signal); if (!controller.signal.aborted) timer = setTimeout(() => void poll(), 2_000) }
      catch (caught) { if (!controller.signal.aborted && mounted.current) setError(`状态读取失败：${failureMessage(caught)}。请重新读取，查询不会调用模型。`) }
    }
    timer = setTimeout(() => void poll(), 2_000)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [active, pendingStatus, read])
  const guard = useCallback(async () => {
    if (lock.current || attempt.current) { setError('请先确认本场待处理请求的结果。'); return false }
    if (textRef.current === savedRef.current) return true
    return dialog({ title: '离开当前作答？', description: '当前答案尚未保存。账号内切换会保留本场草稿；刷新或退出前请保存草稿。', confirmLabel: '保留草稿并离开' })
  }, [dialog])
  useEffect(() => { if (active) registerLocalGuard(id, guard); return () => registerLocalGuard(id, null) }, [id, active, guard, registerLocalGuard])
  const write = async (operation: Attempt['operation'], leave = false) => {
    const current = sessionRef.current
    if (!current || lock.current || confirmedWriteRef.current || (!attempt.current && ['DRAFT', 'SUBMIT'].includes(operation) && (!editable || invalid))) return
    lock.current = true; setBusy(true); setError(null)
    const pending = attempt.current ?? { operation, payload: { expectedVersion: current.version, requestId: crypto.randomUUID() }, turn: current.currentTurn, text: textRef.current, revision: revision.current, leave }
    attempt.current = pending
    try {
      const receipt = pending.operation === 'DRAFT' ? await saveAnswer(id, { ...pending.payload, turnIndex: pending.turn, answerText: pending.text })
        : pending.operation === 'SUBMIT' ? await submitAnswer(id, pending.turn, { ...pending.payload, answerText: pending.text })
        : pending.operation === 'COMPLETE' ? await completeInterview(id, { ...pending.payload, early: true })
        : pending.operation === 'GENERATION' ? await retryGeneration(id, pending.payload)
        : await retryEvaluation(id, pending.payload)
      if (!mounted.current) return
      // The write is confirmed even if its subsequent read fails.
      confirmedWriteRef.current = { operation: pending.operation, version: receipt.resultVersion }; setConfirmedWrite(confirmedWriteRef.current)
      attempt.current = null; setUnknown(false); onChanged()
      if (pending.operation === 'DRAFT') { savedRef.current = pending.text; setSaved(pending.text); notify('草稿已保存，尚未提交', 'success') }
      else notify(pending.operation === 'SUBMIT' ? '答案已提交并锁定' : pending.operation === 'COMPLETE' ? '答卷已冻结，等待评估' : '已请求手动重试', 'success')
      try {
        const authoritative = await read(undefined, pending)
        if (authoritative && pending.leave && activeRef.current) onBack()
      } catch (caught) { if (mounted.current && !isAbort(caught)) setError(`操作已成功，当前状态刷新失败：${failureMessage(caught)}。请重新读取。`) }
    } catch (caught) {
      if (!mounted.current || isAbort(caught)) return
      if (caught instanceof ApiError && caught.status < 500) {
        attempt.current = null; setUnknown(false)
        if (caught.status === 409) {
          setConflictText(pending.text)
          try { await read() } catch { /* Keep the local text available if the authoritative read also fails. */ }
          setError('本场版本已变化。本地输入已保留，已锁定答案不会被覆盖；请读取真实进度。')
        } else if (caught.status === 404) { clearMissing(); setError('本场已删除或不可访问。') }
        else setError(failureMessage(caught))
      } else {
        setUnknown(true); setError('操作响应未确认。原轮次、全文、版本和请求标识已保留，请确认原请求结果。')
        // Submit can be confirmed by the explicit submitted state even for an empty answer.
        try {
          const next = await read()
          if (next && pending.operation === 'SUBMIT' && next.answers.some(value => value.turnIndex === pending.turn && value.status === 'SUBMITTED' && value.answerText === pending.text)) { attempt.current = null; setUnknown(false); setError('已从服务端确认本轮提交成功，答案已锁定。'); onChanged() }
          else if (next && pending.operation === 'COMPLETE' && next.answerStatus === 'COMPLETED') { attempt.current = null; setUnknown(false); setError('已从服务端确认答卷冻结。'); onChanged() }
        } catch { /* Same request remains available for explicit replay. */ }
      }
    } finally { lock.current = false; if (mounted.current) setBusy(false) }
  }
  const finish = async () => {
    const current = sessionRef.current
    if (!current || lock.current || attempt.current || confirmedWriteRef.current || current.answerStatus === 'COMPLETED') return
    const submitted = current.answers.filter(answer => answer.status === 'SUBMITTED').length
    if (await dialog({ title: '提前交卷？', description: `已提交 ${submitted} 轮，未提交 ${current.mainQuestionCount * 2 - submitted} 轮。未提交轮次按 0 分计入整场总分；未提交草稿即使有正文也属于未作答。确认后答卷永久冻结，不能补答。`, confirmLabel: '确认交卷', danger: true })) await write('COMPLETE')
  }
  const back = async () => { if (await guard()) onBack() }
  const question = session?.questions.find(value => value.turnIndex === session.currentTurn)
  const status = session ? sessionStatus(session) : null
  return <div className="interview-session-view" ref={toastRef}>
    {error && <div className="note danger-note" role="alert">{error}</div>}
    {loading && <p role="status">正在读取面试…</p>}
    <div className="session-toolbar"><button type="button" className="secondary" onClick={() => void back()}>返回面试列表</button><button type="button" className="secondary" disabled={busy} onClick={() => void refresh()}>重新读取</button>{!missing && <button type="button" className="danger" disabled={busy} onClick={() => void onDelete(id)}>删除面试</button>}</div>
    {unknown && <button disabled={busy} onClick={() => void write(attempt.current?.operation ?? 'SUBMIT')}>确认原请求结果</button>}
    {conflictText !== null && <section className="note warning-note"><h2>保留的本地输入</h2><label>冲突时的答案<textarea readOnly rows={5} value={conflictText} /></label><p>此文本未覆盖服务端答案，可以复制后查看真实进度。</p><button className="text-button" onClick={() => setConflictText(null)}>关闭保留文本</button></section>}
    {session && <><div className="session-heading"><div><h2>{sessionTitle(session)}</h2><p className="muted">{session.mainQuestionCount} 道主问题 / {session.mainQuestionCount * 2} 轮 · {session.hasResume ? `简历快照版本 ${session.resumeVersion}` : '无简历通用面试'} · {session.hasJd ? '使用 JD 快照' : '无 JD'}</p></div><span className={`status ${status?.className}`}>{status?.label}</span></div>
      {session.generationStatus !== 'SUCCEEDED' && <section className="card card-pad"><h2>{status?.label}</h2><p>整场固定题单生成成功后才能开始作答；不会在页面中补题或动态追加追问。</p>{session.safeFailureCode && <p role="alert">{session.safeFailureCode}</p>}{session.generationStatus === 'FAILED' && <button disabled={busy || unknown || !!confirmedWrite} onClick={() => void write('GENERATION')}>手动重试出题</button>}</section>}
      {session.answerStatus === 'COMPLETED' && <>{session.evaluationStatus === 'FAILED' && <button disabled={busy || unknown || !!confirmedWrite} onClick={() => void write('EVALUATION')}>手动重试评估</button>}<ReportView session={session} report={report} /></>}
      {session.generationStatus === 'SUCCEEDED' && session.answerStatus !== 'COMPLETED' && question && <div className="answer-layout"><section className="card question-card"><div className="q-type">第 {question.turnIndex + 1} / {session.mainQuestionCount * 2} 轮 · {question.type === 'MAIN' ? '主问题' : '固定追问'} {Math.floor(question.turnIndex / 2) + 1}</div><SafeMarkdown text={question.text} />{session.submittedCount > 0 && <div className="locked">此前 {session.submittedCount} 轮已提交并锁定。答案不会因刷新、追问或另一个标签页被修改。</div>}
        <label>你的回答<textarea rows={12} placeholder="可以先暂存；提交成功后不可回改。最多 5,000 字符，空内容也可提交。" value={text} disabled={!editable || unknown} aria-invalid={invalid} onChange={event => { revision.current++; textRef.current = event.target.value; setText(event.target.value) }} /></label><div className="answer-count"><span className={invalid ? 'input-error' : 'tiny muted'}>{chars.toLocaleString('en-US')} / 5,000 字符</span><span className="tiny muted">{dirty ? '草稿尚未保存' : '草稿已保存 / 尚未提交'}</span></div>{invalid && <p className="input-error" role="alert">答案超过 5,000 字符。全文保留，请修改后保存或提交。</p>}
        <div className="answer-actions"><button className="secondary" disabled={busy || unknown || !!confirmedWrite || !editable || invalid} onClick={() => void write('DRAFT')}>保存草稿</button><div className="right"><button className="secondary" disabled={busy || unknown || !!confirmedWrite || !editable || invalid} onClick={() => void write('DRAFT', true)}>保存并离开</button><button disabled={busy || unknown || !!confirmedWrite || !editable || invalid} onClick={() => void write('SUBMIT')}>{busy ? '正在处理…' : question.turnIndex + 1 === session.mainQuestionCount * 2 ? '提交最后一轮并交卷' : question.type === 'MAIN' ? '提交并进入追问 →' : '提交并进入下一题 →'}</button></div></div><div className="note">提交成功后本轮立即锁定。网络中断时读取真实进度或重放原请求，不会盲目推进。</div>
      </section><aside className="card side-card"><h2>本场进度</h2><div className="timeline" role="region" aria-label="面试固定题单与进度" tabIndex={0}>{session.questions.map(turn => { const answer = session.answers.find(value => value.turnIndex === turn.turnIndex); const submitted = answer?.status === 'SUBMITTED'; return <div key={turn.turnIndex} className={`turn ${submitted ? 'done' : turn.turnIndex === session.currentTurn ? 'current' : 'future'}`}><span>{turn.type === 'MAIN' ? '主问题' : '追问'} {Math.floor(turn.turnIndex / 2) + 1}</span><span>{submitted ? '已提交' : turn.turnIndex === session.currentTurn ? '当前' : '待回答'}</span></div> })}</div><div className="warning-note note"><strong>提前交卷</strong><br />未提交轮次按 0 分；暂存后离开不属于交卷。</div><button className="secondary full-button" disabled={busy || unknown || !!confirmedWrite} onClick={() => void finish()}>提前交卷</button></aside></div>}
    </>}
  </div>
}
