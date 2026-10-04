import { expect, type Page, type Route } from '@playwright/test'
import type { InterviewCreate, InterviewReceipt, InterviewReport, InterviewSession, JdAnalysis } from '../src/api/interview'
import type { CurrentResume } from '../src/api/resume'

// Controlled HTTP fixtures prove UI behavior only; they do not prove DB/storage/model fencing.
export const sessionId = 'd9800000-0000-4000-8000-000000000001'
export const secondSessionId = 'd9800000-0000-4000-8000-000000000002'
export const makeSession = (id = sessionId, count = 3): InterviewSession => ({
  id, version: 1, direction: 'JAVA_BACKEND', difficulty: 'MID', mainQuestionCount: count,
  generationStatus: 'SUCCEEDED', answerStatus: 'READY', evaluationStatus: 'NOT_STARTED', currentTurn: 0, submittedCount: 0,
  hasResume: false, hasJd: false, resumeVersion: null, resumeSnapshot: null, jdText: null,
  createdAt: '2026-10-04T00:00:00Z', updatedAt: '2026-10-04T00:00:00Z', safeFailureCode: null,
  questions: Array.from({ length: count * 2 }, (_, turnIndex) => ({ turnIndex, type: turnIndex % 2 ? 'FOLLOW_UP' : 'MAIN', parentMainIndex: turnIndex % 2 ? Math.floor(turnIndex / 2) : null, text: `固定题目 ${turnIndex + 1}：说明一致性、错误恢复与取舍。` })), answers: [],
})
export const makeReport = (session: InterviewSession): InterviewReport => ({
  sessionId: session.id, totalScore: null, overallFeedback: null,
  turns: session.questions.map(question => ({ turnIndex: question.turnIndex, status: session.answers.some(answer => answer.turnIndex === question.turnIndex && answer.status === 'SUBMITTED') ? 'NOT_EVALUATED' : 'UNANSWERED', score: session.answers.some(answer => answer.turnIndex === question.turnIndex && answer.status === 'SUBMITTED') ? null : 0, feedback: null, referencePoints: [] })),
  groups: Array.from({ length: session.mainQuestionCount }, (_, mainIndex) => ({ mainIndex: mainIndex * 2, status: 'FAILED', safeFailureCode: 'MODEL_STRUCTURE_INVALID' })),
})
type Call = { path: string; method: string; data: Record<string, unknown> | null; buffer: Buffer | null }
export async function fixture(page: Page, initial: InterviewSession[] = []) {
  const state = {
    resume: { exists: true, version: 1, markdownText: '# 当前 A', sourceKind: 'PASTE', originalFile: null } as CurrentResume,
    sessions: new Map(initial.map(session => [session.id, session])), reports: new Map<string, InterviewReport>(),
    analyses: new Map<string, JdAnalysis>(), calls: [] as Call[], receipts: new Map<string, InterviewReceipt>(),
    resumeFailOnce: false, createDropOnce: false, submitDropOnce: false, submitConflictOnce: false, jdMismatch: false,
    jdPending: false, jdFailed: false, anonymous: false, accountId: 'd9800000-0000-4000-8000-000000000100',
    holdJd: null as (() => Promise<void>) | null,
  }
  let sequence = 3; const newId = () => `d9800000-0000-4000-8000-${String(sequence++).padStart(12, '0')}`
  const ok = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  const problem = (route: Route, status: number, detail: string) => ok(route, { detail, code: 'FIXTURE_FAILURE' }, status)
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request(); const url = new URL(request.url()); const path = url.pathname; const method = request.method()
    let data: Record<string, unknown> | null = null
    if (request.headers()['content-type']?.includes('application/json')) data = request.postDataJSON() as Record<string, unknown>
    state.calls.push({ path, method, data, buffer: request.postDataBuffer() })
    if (path === '/api/auth/csrf') return ok(route, { token: 'synthetic-fixture-token' })
    if (path === '/api/auth/me') return state.anonymous ? problem(route, 401, '未登录') : ok(route, { id: state.accountId, username: 'interview_fixture', role: 'ADMIN', enabled: true, createdAt: '2026-10-04T00:00:00Z' })
    if (path === '/api/auth/login') { state.anonymous = false; return ok(route, { id: state.accountId, username: 'interview_fixture', role: 'ADMIN', enabled: true, createdAt: '2026-10-04T00:00:00Z' }) }
    if (path === '/api/auth/logout') { state.anonymous = true; return route.fulfill({ status: 204 }) }
    if (path === '/api/auth/activity') return route.fulfill({ status: 204 })
    if (path === '/api/projects' || path === '/api/focus/routines') return ok(route, [])
    if (path === '/api/focus/current') return ok(route, null)
    if (path === '/api/focus/routines/fill-today') return ok(route, { date: '2026-10-04', created: [], blocked: [] })
    if (path === '/api/focus/today') return ok(route, { date: '2026-10-04', focusMs: 0, breakMs: 0, sessionCount: 0, records: [] })
    if (path === '/api/me/resume' && method === 'GET') return ok(route, state.resume)
    if (path === '/api/me/resume' && method === 'PUT') {
      if (state.resumeFailOnce) { state.resumeFailOnce = false; return route.abort('failed') }
      if (data?.expectedVersion !== state.resume.version) return problem(route, 409, '当前简历版本冲突')
      state.resume = { ...state.resume, exists: true, version: state.resume.version + 1, markdownText: String(data?.markdownText ?? ''), sourceKind: data?.mode === 'PASTE' ? 'PASTE' : state.resume.sourceKind, originalFile: data?.mode === 'PASTE' ? null : state.resume.originalFile }
      return ok(route, { requestId: data?.requestId, operation: 'PUT', state: 'SUCCEEDED', resultVersion: state.resume.version, objectId: null, safeFailureCode: null })
    }
    if (path === '/api/me/resume/import') {
      const body = request.postDataBuffer()?.toString('utf8') ?? ''
      const text = /name="markdownText"\r\n\r\n([\s\S]*?)\r\n--/.exec(body)?.[1] ?? ''
      const filename = /filename="([^"]+)"/.exec(body)?.[1] ?? 'import.md'
      state.resume = { exists: true, version: state.resume.version + 1, markdownText: text, sourceKind: 'MD_FILE', originalFile: { id: newId(), fileName: filename, size: 17, sha256: 'f'.repeat(64) } }
      return ok(route, { requestId: newId(), operation: 'IMPORT', state: 'SUCCEEDED', resultVersion: state.resume.version, objectId: state.resume.originalFile?.id, safeFailureCode: null })
    }
    if (path === '/api/me/resume' && method === 'DELETE') { state.resume = { exists: false, version: state.resume.version + 1, markdownText: null, sourceKind: null, originalFile: null }; return ok(route, { state: 'SUCCEEDED', resultVersion: state.resume.version }) }
    if (path === '/api/me/resume/original') return route.fulfill({ contentType: 'text/markdown', body: '# immutable original' })
    if (path === '/api/interviews/page') {
      expect(url.searchParams.get('size')).toBe('5')
      const p = Number(url.searchParams.get('page')); const all = [...state.sessions.values()]
      return ok(route, { items: all.slice(p * 5, p * 5 + 5), page: p, size: 5, totalElements: all.length, totalPages: Math.ceil(all.length / 5) })
    }
    if (path === '/api/interviews/jd/parse') {
      const id = newId(); const analysis: JdAnalysis = { id, version: 1, status: state.jdFailed ? 'FAILED' : state.jdPending ? 'PROCESSING' : 'SUCCEEDED', direction: data?.direction as JdAnalysis['direction'], jdText: String(data?.jdText), result: state.jdPending || state.jdFailed ? null : { matched: !state.jdMismatch, summary: '合成 JD 解析', focusPoints: ['Spring', '数据库'] }, safeFailureCode: state.jdFailed ? 'MODEL_STRUCTURE_INVALID' : null }
      state.analyses.set(id, analysis)
      if (state.holdJd) await state.holdJd()
      return ok(route, analysis, 202)
    }
    const jdMatch = /^\/api\/interviews\/jd\/([^/]+)(\/retry)?$/.exec(path)
    if (jdMatch) {
      const previous = state.analyses.get(jdMatch[1]); if (!previous) return problem(route, 404, '解析不存在')
      if (method === 'DELETE') { state.analyses.delete(previous.id); return route.fulfill({ status: 204 }) }
      if (jdMatch[2]) { const next = { ...previous, status: 'SUCCEEDED' as const, version: previous.version + 1, result: { matched: true, summary: '合成重试成功', focusPoints: ['Spring'] } }; state.analyses.set(next.id, next); return ok(route, next, 202) }
      return ok(route, previous)
    }
    if (path === '/api/interviews' && method === 'POST') {
      const payload = data as unknown as InterviewCreate; const prior = state.receipts.get(payload.requestId)
      if (prior) return ok(route, prior)
      const session = { ...makeSession(newId(), payload.mainQuestionCount), direction: payload.direction, difficulty: payload.difficulty, hasResume: payload.useCurrentResume, resumeVersion: payload.expectedResumeVersion, resumeSnapshot: payload.useCurrentResume ? state.resume.markdownText : null, hasJd: !!payload.jdText, jdText: payload.jdText }
      state.sessions.set(session.id, session)
      const receipt: InterviewReceipt = { requestId: payload.requestId, operation: 'CREATE', state: 'SUCCEEDED', sessionId: session.id, resultVersion: session.version, turnIndex: null }; state.receipts.set(payload.requestId, receipt)
      if (state.createDropOnce) { state.createDropOnce = false; return route.abort('failed') }
      return ok(route, receipt, 202)
    }
    const sessionMatch = /^\/api\/interviews\/([^/]+)(.*)$/.exec(path)
    if (sessionMatch) {
      const current = state.sessions.get(sessionMatch[1]); if (!current) return problem(route, 404, '场次不存在')
      const action = sessionMatch[2]
      if (method === 'DELETE') { state.sessions.delete(current.id); return route.fulfill({ status: 204 }) }
      if (!action) return ok(route, current)
      if (action === '/report') return ok(route, state.reports.get(current.id) ?? makeReport(current))
      if (action === '/answer-draft' || /\/answers\/\d+\/submit$/.test(action)) {
        if (state.submitConflictOnce && action.includes('submit')) { state.submitConflictOnce = false; current.version++; current.currentTurn++; current.submittedCount++; current.answers.push({ turnIndex: 0, status: 'SUBMITTED', answerText: '另一标签页提交', version: current.version }); return problem(route, 409, '版本冲突') }
        const requestId = String(data?.requestId); const prior = state.receipts.get(requestId)
        if (prior) return ok(route, prior)
        if (data?.expectedVersion !== current.version) return problem(route, 409, '本场版本冲突')
        const turn = action === '/answer-draft' ? Number(data?.turnIndex) : Number(/\/answers\/(\d+)/.exec(action)?.[1])
        const answer = { turnIndex: turn, status: action === '/answer-draft' ? 'DRAFT' as const : 'SUBMITTED' as const, answerText: String(data?.answerText ?? ''), version: current.version + 1 }
        current.answers = [...current.answers.filter(value => value.turnIndex !== turn), answer]; current.version++
        if (answer.status === 'SUBMITTED') { current.submittedCount++; current.currentTurn++; current.answerStatus = current.currentTurn === current.questions.length ? 'COMPLETED' : 'IN_PROGRESS'; if (current.answerStatus === 'COMPLETED') current.evaluationStatus = 'FAILED' }
        const receipt: InterviewReceipt = { requestId, operation: answer.status, state: 'SUCCEEDED', sessionId: current.id, resultVersion: current.version, turnIndex: turn }; state.receipts.set(requestId, receipt)
        if (state.submitDropOnce && answer.status === 'SUBMITTED') { state.submitDropOnce = false; return route.abort('failed') }
        return ok(route, receipt)
      }
      if (action === '/complete') { current.answerStatus = 'COMPLETED'; current.evaluationStatus = 'FAILED'; current.version++; return ok(route, { sessionId: current.id, state: 'SUCCEEDED', resultVersion: current.version }) }
      if (action === '/generation/retry') { current.generationStatus = 'SUCCEEDED'; current.answerStatus = 'READY'; current.version++; return ok(route, { sessionId: current.id, state: 'SUCCEEDED', resultVersion: current.version }) }
      if (action === '/evaluation/retry') { current.evaluationStatus = 'SUCCEEDED'; current.version++; const report = makeReport(current); report.totalScore = 0; report.turns = report.turns.map(turn => turn.status === 'NOT_EVALUATED' ? { ...turn, status: 'SCORED', score: 0, feedback: '空答案未提供具体依据。' } : turn); report.groups = report.groups.map(group => ({ ...group, status: 'SUCCEEDED' })); state.reports.set(current.id, report); return ok(route, { sessionId: current.id, state: 'SUCCEEDED', resultVersion: current.version }) }
    }
    if (path.endsWith('/page')) return ok(route, { items: [], page: 0, size: 5, totalElements: 0, totalPages: 0 })
    return problem(route, 404, 'Fixture does not implement this endpoint')
  })
  return state
}
export const openInterview = async (page: Page) => { await page.goto('/#interview'); await expect(page.getByRole('heading', { name: 'AI 面试', exact: true })).toBeVisible() }
export const openProfile = async (page: Page) => { await page.goto('/#profile'); await expect(page.getByLabel('简历 Markdown')).toBeVisible() }
export const createTab = async (page: Page) => { await page.getByRole('group', { name: '面试页面' }).getByRole('button', { name: '新建面试', exact: true }).click(); await expect(page.getByLabel('主问题数量')).toBeVisible() }
