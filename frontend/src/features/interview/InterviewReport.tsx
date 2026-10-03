import type { InterviewReport as Report, InterviewSession } from '../../api/interview'
import { SafeMarkdown } from '../../components/SafeMarkdown'
import { sessionStatus } from './model'

export function InterviewReport({ session, report }: { session: InterviewSession; report: Report | null }) {
  const status = sessionStatus(session)
  return <>
    <section className="card report-hero"><div className="score-box"><div><div className={`score ${report?.totalScore == null ? 'score-pending' : ''}`}>{report?.totalScore == null ? '尚无总分' : <>{report.totalScore}<small>/100</small></>}</div><div className="tiny muted">{session.submittedCount} / {session.mainQuestionCount * 2} 轮已提交</div></div></div><div className="report-copy"><span className={`status ${status.className}`}>{status.label}</span><h2>本场答卷已冻结</h2>{report?.overallFeedback ? <SafeMarkdown text={report.overallFeedback} /> : <p>必要评分尚未完整生成，暂不发布最终总分。已确证的答案与有效反馈保留。</p>}{report?.groups && <div className="chips">{report.groups.map(group => <span key={group.mainIndex} className={`chip ${group.status === 'FAILED' ? 'input-error' : ''}`}>问题组 {Math.floor(group.mainIndex / 2) + 1} · {group.status === 'SUCCEEDED' ? '有效' : group.status === 'FAILED' ? '评估失败' : group.status === 'PENDING' ? '等待评估' : '正在评估'}</span>)}</div>}</div></section>
    <div className="grid two report-details"><section className="card card-pad"><h2>逐轮题目、答案与反馈</h2><div className="feedback-list" role="region" aria-label="逐轮面试反馈" tabIndex={0}>{session.questions.map(question => {
      const answer = session.answers.find(value => value.turnIndex === question.turnIndex)
      const turn = report?.turns.find(value => value.turnIndex === question.turnIndex)
      const submitted = answer?.status === 'SUBMITTED'
      const label = turn?.status === 'SCORED' && turn.score != null ? `${turn.score} 分` : !submitted ? '未提交 / 未作答（0 分）' : '未评估 / 评估失败（无分数）'
      return <article className="feedback" key={question.turnIndex}><strong>{question.type === 'MAIN' ? '主问题' : '追问'} {Math.floor(question.turnIndex / 2) + 1} · {label}</strong><SafeMarkdown text={question.text} /><h3>你的答案</h3>{submitted ? answer.answerText === '' ? <p>已提交（空内容）</p> : <SafeMarkdown text={answer.answerText} /> : <p>未提交 / 未作答{answer?.answerText ? '（暂存内容未计入答卷）' : ''}</p>}{turn?.feedback && <><h3>反馈</h3><SafeMarkdown text={turn.feedback} /></>}{turn?.referencePoints && turn.referencePoints.length > 0 && <><h3>参考要点</h3><ul>{turn.referencePoints.map((value, index) => <li key={index}>{value}</li>)}</ul></>}</article>
    })}</div></section><aside className="card card-pad"><h2>计分说明</h2><p className="muted">所有预定轮次等权。未提交轮次按 0 分；模型评估失败保留“无分数”，暂停发布最终总分并等待手动重试。已提交空内容也属于已作答，需得到有效评估。</p><div className="note">总分由后端按全部 {session.mainQuestionCount * 2} 轮等权计算。查看历史与刷新只读取已保存结果，不会再次调用模型。</div></aside></div>
  </>
}
