package com.aiworkbench.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owned temporary file; orchestration always closes it, including duplicate requests. */
public record ValidatedAttachment(Path path, String fileName, String contentType, long size, String sha256) implements AutoCloseable {
    @Override public void close() throws IOException { Files.deleteIfExists(path); }
}
