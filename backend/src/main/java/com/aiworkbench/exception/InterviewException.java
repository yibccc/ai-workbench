package com.aiworkbench.exception;

import org.springframework.http.HttpStatus;

/** Existing curated ProblemDetail handler provides code/version without raw model details. */
public class InterviewException extends AttachmentException {
    public InterviewException(HttpStatus status,String code,String message) { super(status,code,message); }
    public InterviewException(HttpStatus status,String code,String message,Long version) { super(status,code,message,version); }
}
