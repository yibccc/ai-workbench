import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/http'
import { deleteInterview, listInterviews, type InterviewSession } from '../../api/interview'
import { getResume, type CurrentResume } from '../../api/resume'
import { useDialog } from '../../components/dialogContext'
import type { NavigationGuard, RegisterNavigationGuard } from '../../components/layout/navigationGuard'
import type { ViewId } from '../../components/layout/routes'
import { Pagination } from '../../components/Pagination'
import { RetainedView } from '../../components/RetainedView'
import { SegmentedControl } from '../../components/SegmentedControl'
import { useToast } from '../../components/toastContext'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { usePagedList } from '../../hooks/usePagedList'
import { InterviewCreateForm } from './InterviewCreateForm'
import { InterviewSessionView } from './InterviewSessionView'
import { sessionStatus, sessionTitle } from './model'
import { countCodePoints, failureMessage, isAbort } from './text'
import './interview.css'

type Screen = 'center' | 'create' | 'history' | 'session'
export function InterviewWorkspace({ active, resumeRevision, registerGuard }: {
  active: boolean; resumeRevision: number; registerGuard: RegisterNavigationGuard
}) {
  const dialog = useDialog(); const { notify, toastRef } = useToast<HTMLDivElement>()
  const [screen, setScreen] = useState<Screen>('center'); const [page, setPage] = useState(0)
  const [resume, setResume] = useState<CurrentResume | null>(null); const [resumeError, setResumeError] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null); const [visited, setVisited] = useState<string[]>([])
  const [selectedSession, setSelectedSession] = useState<InterviewSession | null>(null)
  const [error, setError] = useState<string | null>(null); const [createDirty, setCreateDirty] = useState(false)
  const [revision, setRevision] = useState(0); const mounted = useRef(false); const deleting = useRef(new Set<string>())
  const localGuards = useRef(new Map<string, NavigationGuard>()); const currentScreen = useRef(screen); const selectedRef = useRef(selected)
  const createDirtyRef = useRef(false); const resumeOwner = useRef<AbortController | null>(null)
  useBeforeUnload(createDirty)
  useEffect(() => { mounted.current = true; const registry = localGuards.current; return () => { mounted.current = false; resumeOwner.current?.abort(); registry.clear() } }, [])
  const readResume = useCallback(async () => {
    resumeOwner.current?.abort(); const controller = new AbortController(); resumeOwner.current = controller
    try { const next = await getResume(controller.signal); if (!controller.signal.aborted && mounted.current) { setResume(next); setResumeError(null) } }
    catch (caught) { if (mounted.current && !isAbort(caught)) setResumeError(failureMessage(caught)) }
  }, [])
  useEffect(() => { let cancelled = false; if (active) void Promise.resolve().then(() => { if (!cancelled) void readResume() }); return () => { cancelled = true; resumeOwner.current?.abort() } }, [active, resumeRevision, readResume])
  const changed = useCallback(() => setRevision(value => value + 1), [])
  const readSession = useCallback((session: InterviewSession) => { if (selectedRef.current === session.id) setSelectedSession(session) }, [])
  const onListError = useCallback((message: string) => setError(message), [])
  const query = useCallback((signal: AbortSignal) => listInterviews(page, signal), [page])
  const list = usePagedList(query, onListError, setPage, revision)
  const setCreateDirtyValue = useCallback((value: boolean) => { createDirtyRef.current = value; setCreateDirty(value) }, [])
  const registerLocalGuard = useCallback((id: string, guard: NavigationGuard | null) => { if (guard) localGuards.current.set(id, guard); else localGuards.current.delete(id) }, [])
  const leaveCurrent = useCallback(async (next: ViewId = 'interview') => {
    if (currentScreen.current === 'session' && selectedRef.current) {
      const guard = localGuards.current.get(selectedRef.current); if (guard) return guard(next)
    }
    if (currentScreen.current === 'create' && createDirtyRef.current) return dialog({ title: '离开创建页面？', description: '当前配置和 JD 草稿会保留。若有待确认请求，请先返回创建页确认结果；刷新或退出将丢失未保存内容。', confirmLabel: '保留草稿并离开' })
    return true
  }, [dialog])
  useEffect(() => {
    if (!active) return
    registerGuard(next => leaveCurrent(next))
    return () => registerGuard(null)
  }, [active, registerGuard, leaveCurrent])
  const show = (value: Screen) => { currentScreen.current = value; setScreen(value) }
  const switchScreen = async (value: Exclude<Screen, 'session'>) => { if (value === screen || await leaveCurrent()) { show(value); setError(null) } }
  const open = async (id: string) => {
    if (!await leaveCurrent()) return
    selectedRef.current = id; setSelected(id); setSelectedSession(null); setVisited(values => values.includes(id) ? values : [...values, id]); show('session'); setError(null)
  }
  const created = (id: string) => { selectedRef.current = id; setSelected(id); setSelectedSession(null); setVisited(values => values.includes(id) ? values : [...values, id]); show('session'); changed(); notify('新面试已创建', 'success') }
  const remove = async (id: string) => {
    if (deleting.current.has(id)) return
    await dialog({ title: '删除这场面试？', description: '将删除本场题单、答案、JD 原文与解析、评估结果以及创建时的简历文本快照。不会影响个人中心当前简历或其他面试；迟到后台结果不会重新生成本场。', confirmLabel: '确认删除', danger: true, onConfirm: async () => {
      if (!mounted.current || deleting.current.has(id)) return
      deleting.current.add(id)
      try {
        try { await deleteInterview(id) } catch (caught) { if (!(caught instanceof ApiError) || caught.status !== 404) throw caught }
        if (!mounted.current) return
        localGuards.current.delete(id); setVisited(values => values.filter(value => value !== id))
        if (selectedRef.current === id) { selectedRef.current = null; setSelected(null); setSelectedSession(null); show('center') }
        changed(); notify('面试已删除', 'success')
      } finally { deleting.current.delete(id) }
    } })
  }
  const title = screen === 'create' ? '新建面试' : screen === 'session' ? selectedSession?.answerStatus === 'COMPLETED' ? '面试报告' : selectedSession ? sessionTitle(selectedSession) : '正在读取面试' : 'AI 面试'
  const description = screen === 'create' ? '方向决定考察范围，JD 调整侧重点，简历用于结合你的实际经历。' : screen === 'session' && selectedSession ? selectedSession.answerStatus === 'COMPLETED' ? `${sessionTitle(selectedSession)} · ${selectedSession.mainQuestionCount} 道主问题 / ${selectedSession.mainQuestionCount * 2} 轮` : `第 ${Math.min(selectedSession.currentTurn + 1, selectedSession.mainQuestionCount * 2)} / ${selectedSession.mainQuestionCount * 2} 轮 · 本场使用创建时的简历和 JD 快照。` : '新建一场面试，或继续已有会话。不同场次的题单、简历快照和进度互不影响。'
  const visibleItems = list.data?.items ?? []
  return <div className="page interview-workspace" ref={toastRef}>
    <header className="page-header page-head"><div><h1>{title}</h1><p>{description}</p></div><SegmentedControl label="面试页面" value={screen === 'session' ? 'center' : screen} onChange={value => void switchScreen(value)} options={[{ value: 'center', label: '面试中心' }, { value: 'create', label: '新建面试' }, { value: 'history', label: '历史记录' }]} /></header>
    <div className="workspace-scroll" role="region" aria-label="AI 面试内容" tabIndex={0}>
      {error && <div className="note danger-note" role="alert">{error}</div>}
      <div hidden={screen !== 'center' && screen !== 'history'}><div className="grid two"><section className="card card-pad interview-history"><div className="history-heading"><div><h2>{screen === 'history' ? '面试历史' : '最近面试'}</h2><p className="tiny muted">“继续面试”只恢复选中的会话；“新建面试”始终创建新会话。</p></div><button onClick={() => void switchScreen('create')}>＋ 新建面试</button></div>
        {list.loading && <p role="status">正在读取面试历史…</p>}{list.error && <button className="secondary" onClick={() => void list.refresh()}>重新读取面试历史</button>}
        {!list.loading && !list.error && visibleItems.length === 0 && <p className="muted">还没有面试。新建第一场，或在个人中心先保存简历。</p>}
        <div className="list interview-rows" role="region" aria-label="面试历史列表" tabIndex={0}>{visibleItems.map(session => { const state = sessionStatus(session); return <article key={session.id} className="session" data-testid="interview-session" data-session-id={session.id}><div className="session-main"><div className="session-top"><h3>{sessionTitle(session)}</h3><span className={`status ${state.className}`}>{state.label}</span></div><div className="meta"><span>{session.hasResume ? '有简历快照' : '无简历通用面试'}</span><span>{session.hasJd ? '使用 JD' : '无 JD'}</span><span>{session.submittedCount} / {session.mainQuestionCount * 2} 轮已提交</span><time>{new Date(session.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })}</time></div><div className="progress"><span style={{ width: `${session.submittedCount / (session.mainQuestionCount * 2) * 100}%` }} /></div>{session.evaluationStatus === 'FAILED' && <p className="tiny input-error">模型评估未完整生成，未发布总分。答卷已锁定。</p>}</div><div className="session-actions"><button className="secondary" onClick={() => void remove(session.id)}>删除</button><button onClick={() => void open(session.id)}>{session.answerStatus === 'COMPLETED' ? '查看答卷 / 报告' : session.generationStatus === 'SUCCEEDED' ? '继续面试' : '查看出题状态'}</button></div></article> })}</div>
        <Pagination page={page} totalPages={list.data?.totalPages ?? 0} onPage={setPage} loading={list.loading} />
      </section><aside className="card card-pad"><h2>当前简历</h2><p className="muted">新建带简历面试时，只能使用个人中心当前已保存的这一份简历。</p>{resumeError ? <><p className="input-error" role="alert">{resumeError}</p><button className="secondary" onClick={() => void readResume()}>重新读取简历</button></> : resume ? <div className={`resume-box ${resume.exists ? 'active' : ''}`}><strong>{resume.exists ? resume.originalFile?.fileName ?? '粘贴文本简历' : '无当前简历'}</strong><span>{resume.exists ? `${countCodePoints(resume.markdownText ?? '').toLocaleString('en-US')} / 20,000 字符 · 版本 ${resume.version}` : '可以直接进行无简历通用面试'}</span></div> : <p role="status">正在读取简历…</p>}<div className="note">更新当前简历只影响之后新建的面试；已有会话继续使用创建时的文本快照。</div><a className="secondary profile-link" href="#profile">进入个人中心管理简历</a></aside></div></div>
      <RetainedView active={screen === 'create'}><InterviewCreateForm resume={resume} active={active && screen === 'create'} onCreated={created} onCancel={() => show('center')} onRefreshResume={readResume} onDirty={setCreateDirtyValue} /></RetainedView>
      {visited.map(id => <RetainedView key={id} active={screen === 'session' && selected === id}><InterviewSessionView id={id} active={active && screen === 'session' && selected === id} onBack={() => show('center')} onDelete={remove} onChanged={changed} onRead={readSession} registerLocalGuard={registerLocalGuard} /></RetainedView>)}
    </div>
  </div>
}
