# Private Text Interviews

## 1. Scope / Trigger

Use for V20, InterviewController/Service/Persistence/Mapper, InterviewAiGateway/AgentScope adapter, durable jobs, report reads and deletion. This is the existing Java17/Boot3.5/MyBatis/AgentScope single backend, not an imported SpringAI/JPA service. Private data stays owner-scoped, including ADMIN.

## 2. Signatures

- POST /api/interviews {requestId,direction,difficulty,mainQuestionCount,useCurrentResume,expectedResumeVersion?,jdText?,jdAnalysisId?} -> Receipt(requestId,operation,state,sessionId,resultVersion,turnIndex).
- GET /api/interviews/page?page=0&size=5 -> PageResponse<Summary>; GET /{id} -> Session.
- POST /jd/parse {direction,jdText,requestId} ->202 JdAnalysis(id,version,status,direction,jdText,result{matched,summary,focusPoints},safeFailureCode).
- GET /jd/{id}; POST /jd/{id}/retry {expectedVersion,requestId}; DELETE /jd/{id} ->204.
- PUT /{id}/answer-draft {expectedVersion,requestId,answerText,turnIndex}; POST /{id}/answers/{turn}/submit with the same typed write fields.
- POST /{id}/complete {expectedVersion,requestId,early}; POST /{id}/generation/retry and /evaluation/retry {expectedVersion,requestId}.
- GET /{id}/report -> Report(sessionId,turns,groups,totalScore,overallFeedback); DELETE /{id} ->204.
- V20: interview_sessions, interview_questions, interview_answers, interview_jd_analyses, interview_ai_jobs, interview_evaluations and interview_operation_receipts.
- workbench.interview.timeout=PT4M, lease-duration=PT5M, queue-duration=PT45M; positive and lease>timeout. interviewTaskExecutor uses core/max2, queue40; recovery scans every30s.

## 3. Contracts

Direction is exactly JAVA_BACKEND, REACT_FRONTEND, AGENT_DEVELOPMENT or FULL_STACK; difficulty JUNIOR/MID/SENIOR. Every session freezes server current text/version/hash (or no resume), JD original/result, direction/difficulty/count and question/rubric/model identifiers. Current changes never alter an existing session. Use ResumePersistenceService.snapshotForInterview inside the creation transaction; do not accept client owner/resumeId/replacement snapshot.

N is any integer3..20, UI default5; each main has one nonempty pre-generated follow-up, exactly2N turns. Main turn=2g, follow=2g+1, parentMainIndex=2g. Generate the entire set and validate IDs/types/parent/text/cardinality before one atomic READY transition. Never repair bad/partial output with another implicit call or deterministic fallback.

JD is optional pasted text at most10,000 code points. An explicit parse creates persistent owner/direction/exact UTF8 hash+result. Creation requires matching raw text/direction and successful analysis, copies the snapshot, and does not consume a reusable successful analysis. Editing/removing/cancelling explicitly fences/deletes its draft analysis; existing sessions retain copies. Mismatch/failure preserves raw input and never changes direction or silently ignores JD. No fixed3–7 focus-point product quota.

InterviewJson enforces exact enum/text/bool/integer JSON tokens locally, without changing other feature ObjectMapper rules. Answer text at most5,000 code points; empty draft and empty SUBMITTED are allowed. Draft saves do not advance. Submit atomically locks the current answer, writes a receipt and advances once. Same canonical owner/operation/requestId replay is checked before an old version and returns the original outcome; another payload409. New create IDs always create independent sessions. Get the authoritative Session after a write; minimal receipt is not the latest state.

Generation=PENDING/PROCESSING/SUCCEEDED/FAILED; answer=NOT_READY/READY/IN_PROGRESS/COMPLETED; evaluation=NOT_STARTED/PENDING/PROCESSING/SUCCEEDED/FAILED. Last submit or early complete freezes the whole answer sheet and authorizes first evaluation once. Never unlock on evaluation failure. Only never-submitted turns become UNANSWERED; their drafts are not silently submitted.

Evaluate each main+follow-up group with fixed question/answer/rubric/model hash. Successful groups are reusable; manual retry only schedules necessary failed/non-success groups. Fully unanswered groups need no model. Every SUBMITTED index, including empty text, needs one finite0..100 score and concrete feedback; missing/duplicate/extra/incorrect IDs, types or truncation fail the group. UNANSWERED is always0 independently of group PENDING/FAILED; a submitted system-failed turn is NOT_EVALUATED/null. Do not hide known unanswered zero behind a failed group. Publish total only when all required results are valid, mean=sum/(2N), BigDecimal2 decimals HALF_UP. Reads use persisted results without regeneration or extra summary-model calls.

Persistent jobs use JD_PARSE/GENERATION/EVALUATION_GROUP. PENDING is queue state with an independent deadline; PROCESSING token/lease begin when the worker actually claims, not at enqueue. Only initial user parse/create/complete or an explicit retry dispatches a new attempt. Rejected/expired queue and expired leases become safe FAILED; startup/periodic recovery never implicitly retries models. Valid leases are not blindly stolen.

Terminal result writes require owner, input hash, active token, PROCESSING, existing resource and unexpired DB wall-clock lease. Make token CAS the last write after all question/answer/score/session subwrites, using clock_timestamp(); zero rows throws to roll back the whole transaction. CURRENT_TIMESTAMP is transaction start and fails this contract after lock/trigger delays.

AgentScope2.0.3 defaults to3 attempts. Set GenerateOptions.executionConfig(ExecutionConfig.builder().timeout(operationTimeout).maxAttempts(1).build()) for every interview operation; outer blockLast alone is insufficient. JSON output decoder is an isolated strict ObjectMapper copy, never a global coercion change. Server rules/enum/count belong in SystemMessage and complete untrusted_input_data JSON in UserMessage; no tools, remote fetch, JSON-repair call or model fallback. A message structure test does not prove real-model prompt robustness.

All new private JSON/errors have no-store,private. CurrentUser supplies HTTP owner, durable row supplies async owner; every SQL/association includes it. Same-owner composite FKs reinforce authorization. GET/page/detail/JD/report/poll neither call models nor signal explicit activity. Delete any stage wipes session text/JD/questions/drafts/answers/evaluation/snapshot and invalidates jobs, preserving only minimal no-body receipts/tombstones to reject create replay. It never affects current resume or another session.

## 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Anonymous/foreign business UUID | 401/404, no private state/content |
| Missing enum/required choice, numeric enum, fractional count/version/turn or wrong scalar type | 400, no progress mutation |
| Stale progress / changed same request payload | 409, locked text unchanged |
| JD failed/mismatched/hash or owner differs | Preserve draft; no silent no-JD creation |
| Bad question set/model structure | FAILED, zero partial READY rows, manual retry only |
| Bad/missing score or failed group | Submitted score/total null; unanswered still0 |
| Old/expired token, deleted resource, delayed subwrite across lease | Zero terminal write; all subwrites roll back |
| Read/replay/successful-group reuse | Zero new model requests |

## 5. Good / Base / Bad Cases

Good: two independent sessions keep resume A/B snapshots and different progress; a failed mixed evaluation shows submitted=null/unanswered=0 and later manual retry preserves known results. Base: no-resume general interview and all-unanswered early completion are valid. Bad: global ADMIN reads, dynamic follow-ups, SDK retry hidden under blockLast, averaged-only-answered scoring, failed=0, transaction-start lease checks, clearing create receipts on delete, or creating another model call from GET.

## 6. Tests Required

Real owner HTTP/PG concurrency: same request/different payload409/different owner, JD source/hash/mismatch/removal+successful create replay, 3/5/20 strict sets, snapshot change/delete, draft/submit lock/response loss, freeze/submit race, exact scalars and no mutation. Validate fixed scores/rounding, mixed group pending/failed/retry and zero GET calls, success-group reuse, all-stage deletion and old token rejection. Inject queued rejection and real JVM kill/new-JVM recovery with zero model calls until manual retry. Delay inserts across lease and directly assert zero child rows. Exercise maximal20main/40turn/20kresume/10kJD/5kanswers without truncation.

Use real SDK+local counting HTTP for503/429/timeout/disconnect/badJSON and exactly one send per attempt. Capture JD/generation/evaluation messages, private markers and full maximal data; assert only system server rules and user data, no tools, and manual second operation only. Keep live model keys empty; real supplier capacity/quality/latency/cost remain a separately authorized measurement.

## 7. Wrong vs Correct

Wrong: process a group then turn failed scores into zero, or finish a job before writing children. Correct: preserve submitted=null and UNANSWERED=0, write children under short locks, then perform the final clock_timestamp/token CAS and roll back if it fails. Wrong: assume a mock gateway proves no SDK retry. Correct: count actual HTTP requests through AgentScope2.0.3.
