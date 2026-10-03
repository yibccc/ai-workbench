import { request } from './http'
import { WORKSPACE_PAGE_SIZE, type PageResponse } from './pagination'

export type InterviewDirection = 'JAVA_BACKEND' | 'REACT_FRONTEND' | 'AGENT_DEVELOPMENT' | 'FULL_STACK'
export type InterviewDifficulty = 'JUNIOR' | 'MID' | 'SENIOR'
export type JobStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED'
export type InterviewQuestion = { turnIndex: number; type: 'MAIN' | 'FOLLOW_UP'; parentMainIndex: number | null; text: string }
export type InterviewAnswer = { turnIndex: number; status: 'DRAFT' | 'SUBMITTED' | 'UNANSWERED'; answerText: string; version: number }
export type InterviewSession = {
  id: string; version: number; direction: InterviewDirection; difficulty: InterviewDifficulty; mainQuestionCount: number
  generationStatus: JobStatus; answerStatus: 'NOT_READY' | 'READY' | 'IN_PROGRESS' | 'COMPLETED'
  evaluationStatus: 'NOT_STARTED' | JobStatus; currentTurn: number; submittedCount: number
  hasResume: boolean; hasJd: boolean; resumeVersion: number | null; resumeSnapshot: string | null
  jdText: string | null; createdAt: string; updatedAt: string; questions: InterviewQuestion[]; answers: InterviewAnswer[]; safeFailureCode: string | null
}
export type InterviewSummary = Omit<InterviewSession, 'resumeVersion' | 'resumeSnapshot' | 'jdText' | 'questions' | 'answers'>
export type InterviewCreate = {
  requestId: string; direction: InterviewDirection; difficulty: InterviewDifficulty; mainQuestionCount: number
  useCurrentResume: boolean; expectedResumeVersion: number | null; jdText: string | null; jdAnalysisId: string | null
}
export type InterviewReceipt = { requestId: string; operation: string; state: string; sessionId: string; resultVersion: number; turnIndex: number | null }
export type JdAnalysis = {
  id: string; version: number; status: JobStatus; direction: InterviewDirection; jdText: string
  result: { matched: boolean; summary: string; focusPoints: string[] } | null; safeFailureCode: string | null
}
export type ReportTurn = { turnIndex: number; status: 'SCORED' | 'UNANSWERED' | 'NOT_EVALUATED'; score: number | null; feedback: string | null; referencePoints: string[] }
export type InterviewReport = { sessionId: string; totalScore: number | null; overallFeedback: string | null; turns: ReportTurn[]; groups: { mainIndex: number; status: JobStatus; safeFailureCode: string | null }[] }
export type VersionOperation = { expectedVersion: number; requestId: string }
export type AnswerOperation = VersionOperation & { answerText: string }
const json = (method: string, payload: unknown) => ({ method, body: JSON.stringify(payload) })
export const listInterviews = (page: number, signal?: AbortSignal) => request<PageResponse<InterviewSummary>>(`/api/interviews/page?page=${page}&size=${WORKSPACE_PAGE_SIZE}`, { signal })
export const getInterview = (id: string, signal?: AbortSignal) => request<InterviewSession>(`/api/interviews/${id}`, { signal })
export const createInterview = (payload: InterviewCreate) => request<InterviewReceipt>('/api/interviews', json('POST', payload))
export const parseJd = (payload: { direction: InterviewDirection; jdText: string; requestId: string }) => request<JdAnalysis>('/api/interviews/jd/parse', json('POST', payload))
export const getJd = (id: string, signal?: AbortSignal) => request<JdAnalysis>(`/api/interviews/jd/${id}`, { signal })
export const retryJd = (id: string, payload: VersionOperation) => request<JdAnalysis>(`/api/interviews/jd/${id}/retry`, json('POST', payload))
export const deleteJd = (id: string) => request<void>(`/api/interviews/jd/${id}`, { method: 'DELETE' })
export const saveAnswer = (id: string, payload: AnswerOperation & { turnIndex: number }) => request<InterviewReceipt>(`/api/interviews/${id}/answer-draft`, json('PUT', payload))
export const submitAnswer = (id: string, turn: number, payload: AnswerOperation) => request<InterviewReceipt>(`/api/interviews/${id}/answers/${turn}/submit`, json('POST', payload))
export const completeInterview = (id: string, payload: VersionOperation & { early: true }) => request<InterviewReceipt>(`/api/interviews/${id}/complete`, json('POST', payload))
export const retryGeneration = (id: string, payload: VersionOperation) => request<InterviewReceipt>(`/api/interviews/${id}/generation/retry`, json('POST', payload))
export const retryEvaluation = (id: string, payload: VersionOperation) => request<InterviewReceipt>(`/api/interviews/${id}/evaluation/retry`, json('POST', payload))
export const getInterviewReport = (id: string, signal?: AbortSignal) => request<InterviewReport>(`/api/interviews/${id}/report`, { signal })
export const deleteInterview = (id: string) => request<void>(`/api/interviews/${id}`, { method: 'DELETE' })
