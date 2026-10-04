package com.aiworkbench.dto.interview;

import com.aiworkbench.config.InterviewJson;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InterviewModels {
    private InterviewModels() {}
    public enum Direction { JAVA_BACKEND, REACT_FRONTEND, AGENT_DEVELOPMENT, FULL_STACK }
    public enum Difficulty { JUNIOR, MID, SENIOR }
    public record Create(UUID requestId,@JsonDeserialize(using=InterviewJson.ExactDirection.class) Direction direction,
            @JsonDeserialize(using=InterviewJson.ExactDifficulty.class) Difficulty difficulty,
            @JsonDeserialize(using=InterviewJson.ExactInteger.class) Integer mainQuestionCount,
            @JsonDeserialize(using=InterviewJson.ExactBoolean.class) Boolean useCurrentResume,
            @JsonDeserialize(using=InterviewJson.ExactLong.class) Long expectedResumeVersion,
            @JsonDeserialize(using=InterviewJson.ExactText.class) String jdText,UUID jdAnalysisId) {}
    public record ParseJd(@JsonDeserialize(using=InterviewJson.ExactDirection.class) Direction direction,
            @JsonDeserialize(using=InterviewJson.ExactText.class) String jdText,UUID requestId) {}
    public record VersionOperation(@JsonDeserialize(using=InterviewJson.ExactLong.class) Long expectedVersion,UUID requestId) {}
    public record AnswerOperation(@JsonDeserialize(using=InterviewJson.ExactLong.class) Long expectedVersion,UUID requestId,
            @JsonDeserialize(using=InterviewJson.ExactText.class) String answerText,
            @JsonDeserialize(using=InterviewJson.ExactInteger.class) Integer turnIndex) {}
    public record Complete(@JsonDeserialize(using=InterviewJson.ExactLong.class) Long expectedVersion,UUID requestId,
            @JsonDeserialize(using=InterviewJson.ExactBoolean.class) Boolean early) {}
    public record Receipt(UUID requestId,String operation,String state,UUID sessionId,long resultVersion,Integer turnIndex) {}
    public record JdResult(boolean matched,String summary,List<String> focusPoints) {}
    public record JdAnalysis(UUID id,long version,String status,Direction direction,String jdText,JdResult result,String safeFailureCode) {}
    public record Question(int turnIndex,String type,Integer parentMainIndex,String text) {}
    public record Answer(int turnIndex,String status,String answerText,long version) {}
    public record Session(UUID id,long version,Direction direction,Difficulty difficulty,int mainQuestionCount,
            String generationStatus,String answerStatus,String evaluationStatus,int currentTurn,int submittedCount,
            boolean hasResume,boolean hasJd,Long resumeVersion,String resumeSnapshot,String jdText,
            Instant createdAt,Instant updatedAt,List<Question> questions,List<Answer> answers,String safeFailureCode) {}
    public record Summary(UUID id,long version,Direction direction,Difficulty difficulty,int mainQuestionCount,
            String generationStatus,String answerStatus,String evaluationStatus,int currentTurn,int submittedCount,
            boolean hasResume,boolean hasJd,Instant createdAt,Instant updatedAt,String safeFailureCode) {}
    public record ReportTurn(int turnIndex,String status,BigDecimal score,String feedback,List<String> referencePoints) {}
    public record ReportGroup(int mainIndex,String status,String safeFailureCode) {}
    public record Report(UUID sessionId,List<ReportTurn> turns,List<ReportGroup> groups,BigDecimal totalScore,String overallFeedback) {}
    public record QuestionInput(Direction direction,Difficulty difficulty,int mainQuestionCount,String resumeText,String jdText,JdResult jdResult,
            String questionVersion,String rubricVersion,String modelVersion) {}
    public record GroupInput(Direction direction,Difficulty difficulty,String resumeText,String jdText,JdResult jdResult,
            List<Question> questions,List<Answer> answers,String rubricVersion,String modelVersion) {}
    public record JdInput(Direction direction,String jdText) {}
    public record QuestionSet(List<Question> questions) {}
    public record Score(int turnIndex,BigDecimal score,String feedback,List<String> referencePoints) {}
    public record GroupScore(List<Score> turns) {}
}
