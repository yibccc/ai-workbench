import type { InterviewDifficulty, InterviewDirection, InterviewSummary } from '../../api/interview'
export const directions: readonly { value: InterviewDirection; label: string; description: string }[] = [
  { value: 'JAVA_BACKEND', label: 'Java 后端', description: 'Java / Spring / 数据库 / 缓存 / 工程实践' },
  { value: 'REACT_FRONTEND', label: 'React 前端', description: 'TypeScript / React / 浏览器 / 性能 / 工程化' },
  { value: 'AGENT_DEVELOPMENT', label: 'Agent 开发', description: 'LLM / Tool / Context / RAG / 评估与恢复' },
  { value: 'FULL_STACK', label: '全栈', description: '页面到接口、数据、鉴权、部署的跨层设计' },
]
export const difficultyLabels: Record<InterviewDifficulty, string> = { JUNIOR: '初级', MID: '中级', SENIOR: '高级' }
export const directionLabel = (direction: InterviewDirection) => directions.find(item => item.value === direction)?.label ?? direction
export const sessionTitle = (session: InterviewSummary) => `${directionLabel(session.direction)} · ${difficultyLabels[session.difficulty]}`
export const sessionStatus = (session: InterviewSummary): { label: string; className: string } => {
  if (session.generationStatus === 'FAILED') return { label: '出题失败', className: 'failed' }
  if (session.generationStatus !== 'SUCCEEDED') return { label: session.generationStatus === 'PENDING' ? '等待出题' : '正在出题', className: 'waiting' }
  if (session.answerStatus !== 'COMPLETED') return { label: '进行中', className: 'running' }
  if (session.evaluationStatus === 'FAILED') return { label: '评估失败', className: 'failed' }
  if (session.evaluationStatus === 'SUCCEEDED') return { label: '已完成', className: 'done' }
  return { label: session.evaluationStatus === 'PROCESSING' ? '正在评估' : '等待评估', className: 'waiting' }
}
