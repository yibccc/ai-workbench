package com.aiworkbench.storage;

public class StorageException extends RuntimeException {
    public StorageException(Throwable cause) { super("Object storage is unavailable", cause); }
}
