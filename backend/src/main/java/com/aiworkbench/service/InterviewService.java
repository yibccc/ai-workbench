package com.aiworkbench.service;

import com.aiworkbench.common.PageResponse;
import com.aiworkbench.dto.interview.InterviewModels.*;
import java.util.UUID;

public interface InterviewService {
    Receipt create(Create request);
    Session session(UUID id);
    PageResponse<Summary> page(int page,int size);
    JdAnalysis parseJd(ParseJd request);
    JdAnalysis jd(UUID id);
    JdAnalysis retryJd(UUID id,VersionOperation request);
    void deleteJd(UUID id);
    Receipt draft(UUID id,AnswerOperation request);
    Receipt submit(UUID id,int turn,AnswerOperation request);
    Receipt complete(UUID id,Complete request);
    Receipt retry(UUID id,VersionOperation request,boolean generation);
    Report report(UUID id);
    void delete(UUID id);
}
