package com.aiworkbench.entity.interview;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InterviewRows {
    private InterviewRows() {}
    public record SessionRow(UUID id,UUID userId,long version,String direction,String difficulty,int mainCount,
            String generationStatus,String answerStatus,String evaluationStatus,int currentTurn,int submittedCount,
            String resumeText,Long resumeVersion,String resumeHash,String jdText,String jdResult,String inputHash,
            String modelVersion,String questionVersion,String rubricVersion,boolean deleted,BigDecimal totalScore,
            String overallFeedback,String safeFailureCode,Instant createdAt,Instant updatedAt) {}
    public record QuestionRow(UUID sessionId,UUID userId,int turnIndex,String type,Integer parentMainIndex,String text) {}
    public record AnswerRow(UUID sessionId,UUID userId,int turnIndex,String status,String answerText,long version) {}
    public record JdRow(UUID id,UUID userId,long version,String direction,String jdText,String rawHash,String status,
            String resultJson,boolean deleted,String safeFailureCode,Instant createdAt,Instant updatedAt) {}
    public record JobRow(UUID id,UUID userId,String kind,UUID sessionId,UUID jdId,Integer mainIndex,String inputHash,
            String payloadJson,String status,UUID token,Instant leaseExpiresAt,Instant queueDeadline,String safeFailureCode,
            Instant createdAt,Instant updatedAt) {}
    public record EvaluationRow(UUID sessionId,UUID userId,int mainIndex,String inputHash,String status,
            String resultJson,String safeFailureCode) {}
    public record ReceiptRow(UUID userId,String operation,UUID requestId,String payloadHash,UUID sessionId,UUID jdId,
            String state,long resultVersion,Integer turnIndex,Instant createdAt) {}
}
