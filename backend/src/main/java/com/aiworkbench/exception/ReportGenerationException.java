package com.aiworkbench.exception;



public final class ReportGenerationException extends IllegalArgumentException {
    public enum Code {
        MODEL_TIMEOUT, OUTPUT_TRUNCATED, INVALID_JSON, UNKNOWN_SOURCE,
        SOURCE_ROLE_MISMATCH, INVALID_BULLET, MODEL_UNAVAILABLE
    }

    private final Code code;
    private final String stage;

    public ReportGenerationException(Code code, String stage, String message) {
        super(message);
        this.code = code;
        this.stage = stage;
    }

    public ReportGenerationException(Code code, String stage, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.stage = stage;
    }

    public Code code() { return code; }
    public String stage() { return stage; }
}
