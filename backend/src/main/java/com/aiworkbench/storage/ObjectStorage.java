package com.aiworkbench.storage;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Business identity and authorization live outside the provider adapter. */
public interface ObjectStorage {
    void put(String key, Path validatedFile, long size, String contentType, String sha256);
    StoredObject open(String key);
    void delete(String key);
    interface StoredObject extends AutoCloseable {
        InputStream stream();
        long size();
        @Override void close() throws IOException;
    }
}
