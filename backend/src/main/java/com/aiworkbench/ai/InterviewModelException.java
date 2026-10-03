package com.aiworkbench.ai;

public class InterviewModelException extends RuntimeException {
    private final String code;
    public InterviewModelException(String code) { super("Interview model operation failed"); this.code=code; }
    public String code() { return code; }
}
