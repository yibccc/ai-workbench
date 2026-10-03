package com.aiworkbench.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only curated codes and messages cross the HTTP boundary. */
public class AttachmentException extends ResponseStatusException {
    private final String code;
    private final Long currentVersion;
    public AttachmentException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }
    public AttachmentException(HttpStatus status, String code, String message, Long currentVersion) {
        super(status, message);
        this.code = code;
        this.currentVersion = currentVersion;
    }
    public String code() { return code; }
    public Long currentVersion() { return currentVersion; }
}
