package com.aiworkbench.mapper;

import com.aiworkbench.entity.interview.InterviewRows.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InterviewMapper {
    Optional<UUID> lockOwner(@Param("owner") UUID owner);
    Optional<SessionRow> session(@Param("owner") UUID owner,@Param("id") UUID id);
    Optional<SessionRow> lockSession(@Param("owner") UUID owner,@Param("id") UUID id);
    List<SessionRow> sessions(@Param("owner") UUID owner);
    int insertSession(@Param("row") SessionRow row);
    int updateSession(@Param("row") SessionRow row,@Param("expected") long expected);
    List<QuestionRow> questions(@Param("owner") UUID owner,@Param("id") UUID id);
    List<AnswerRow> answers(@Param("owner") UUID owner,@Param("id") UUID id);
    int insertQuestion(@Param("row") QuestionRow row);
    int insertAnswer(@Param("row") AnswerRow row);
    int updateAnswer(@Param("row") AnswerRow row);
    Optional<JdRow> jd(@Param("owner") UUID owner,@Param("id") UUID id);
    Optional<JdRow> lockJd(@Param("owner") UUID owner,@Param("id") UUID id);
    int insertJd(@Param("row") JdRow row);
    int updateJd(@Param("row") JdRow row,@Param("expected") long expected);
    Optional<ReceiptRow> receipt(@Param("owner") UUID owner,@Param("operation") String operation,@Param("request") UUID request);
    int insertReceipt(@Param("row") ReceiptRow row);
    int insertJob(@Param("row") JobRow row);
    Optional<JobRow> job(@Param("owner") UUID owner,@Param("id") UUID id);
    Optional<JobRow> lockJob(@Param("owner") UUID owner,@Param("id") UUID id);
    int claimJob(@Param("owner") UUID owner,@Param("id") UUID id,@Param("token") UUID token,@Param("lease") Instant lease,@Param("now") Instant now);
    int finishJob(@Param("owner") UUID owner,@Param("id") UUID id,@Param("token") UUID token,@Param("hash") String hash,@Param("status") String status,@Param("code") String code);
    int failJob(@Param("owner") UUID owner,@Param("id") UUID id,@Param("status") String status,@Param("token") UUID token,@Param("code") String code);
    List<JobRow> expiredJobs();
    List<EvaluationRow> evaluations(@Param("owner") UUID owner,@Param("id") UUID id);
    int insertEvaluation(@Param("row") EvaluationRow row);
    int updateEvaluation(@Param("row") EvaluationRow row);
    int deleteSessionJobs(@Param("owner") UUID owner,@Param("id") UUID id);
    int deleteJdJobs(@Param("owner") UUID owner,@Param("id") UUID id);
    int deleteAnswers(@Param("owner") UUID owner,@Param("id") UUID id);
    int deleteQuestions(@Param("owner") UUID owner,@Param("id") UUID id);
    int deleteEvaluations(@Param("owner") UUID owner,@Param("id") UUID id);
}
