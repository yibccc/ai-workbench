package com.aiworkbench.exception;

import org.springframework.http.HttpStatus;

/** Uses the existing curated ProblemDetail/code translation. */
public class ResumeException extends AttachmentException {
    public ResumeException(HttpStatus status, String code, String message) { super(status, code, message); }
    public ResumeException(HttpStatus status, String code, String message, Long version) { super(status, code, message, version); }
}
