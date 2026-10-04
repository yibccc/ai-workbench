# 实施期 typed API 对齐

本记录补充父design中的DTO字段命名，不改变FR/AC。2026-10-04，resume真实Java已compile，UI已按字段迁入；engine创建时采用此合同并与实际api/interview.ts再次对照。

## Resume 已实际实现

Current(exists,version,markdownText,sourceKind,originalFile{id,fileName,size,sha256})；Receipt(requestId,operation,state,resultVersion,objectId,safeFailureCode)，成功/未知写后GET current采用权威。DELETE query expectedVersion/requestId；PUT必mode=EDIT_CURRENT|PASTE，import四parts。Snapshot service `ResumePersistenceService.snapshotForInterview(UUID,long)` @Transactional(MANDATORY)，返回ResumeModels.Snapshot(exists,version,markdownText,contentSha256)，engine由调用者短事务使用并检查存在/版本。

## Interview 前后端统一目标

- 写：InterviewReceipt(requestId,operation,state,sessionId,resultVersion,turnIndex?)；create/draft/submit/complete/retry同ID重放，随后ownerGET Session，不新建receipt恢复GET。
- JD parse/retry202：JdAnalysis(id,version,status,direction,jdText,result,safeFailureCode?)；result={matched:boolean,summary:string,focusPoints:string[]}，不固定3–7。成功matchedtrue；不匹配保raw并失败/提示，不换direction或静默忽略。GET纯读，改文/方向、明确移除/确认取消才DELETE，同成功分析多场copy。
- Question(turnIndex,type MAIN|FOLLOW_UP,parentMainIndex:number|null,text)，0起点main2g/follow2g+1。
- Answer(turnIndex,status DRAFT|SUBMITTED|UNANSWERED,answerText,version)，空SUBMITTED仍提交；早交卷未提交才UNANSWERED。
- Session detail：id/version/direction/difficulty/mainQuestionCount、generationStatus/answerStatus/evaluationStatus、currentTurn、questions/answers、hasResume/hasJd/submittedCount、createdAt/updatedAt和安全错误。列表尽量用不带大正文/答案的摘要projection，UI不依赖list具有detail arrays。
- Report：turns(turnIndex,status SCORED|UNANSWERED|NOT_EVALUATED,score:number|null,feedback,referencePoints?)、groups(status...)、totalScore:number|null、overallFeedback；必要评分不全总分null，失败不得??0。后端平均两位HALF_UP，前端不额外整数化改变分数。
- 实际ReportGroup.mainIndex为主问题turnIndex=0/2/4而非组序号，显示floor(index/2)+1；父接口不改，UI/fixture按真实字段语义对齐。

实际JSON property统一，dto/backend与api/frontend类型编译及HTTP契约测试是最终依据；不让旧研究的1起点/QUEUED/consume/空答最小长度反改已批准方案。持久owner/CSRF、no-store、receipt/fencing等仍按父design。
