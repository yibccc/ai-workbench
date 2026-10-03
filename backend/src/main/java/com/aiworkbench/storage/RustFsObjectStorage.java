package com.aiworkbench.storage;

import com.aiworkbench.config.StorageProperties;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

@Component
@EnableConfigurationProperties(StorageProperties.class)
public class RustFsObjectStorage implements ObjectStorage {
    private final StorageProperties properties;
    private S3Client client;
    public RustFsObjectStorage(StorageProperties properties) { this.properties = properties; }

    private synchronized S3Client client() {
        if (client != null) return client;
        if (properties.accessKey() == null || properties.accessKey().isBlank()
                || properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new StorageException(new IllegalStateException("Storage application credentials are required"));
        }
        client = S3Client.builder().endpointOverride(properties.endpoint()).region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).chunkedEncodingEnabled(false).build())
                .httpClientBuilder(Apache5HttpClient.builder().connectionTimeout(properties.connectTimeout())
                        .socketTimeout(properties.readTimeout()).connectionAcquisitionTimeout(properties.connectTimeout()).maxConnections(16))
                .overrideConfiguration(config -> config.apiCallAttemptTimeout(properties.apiCallAttemptTimeout())
                        .apiCallTimeout(properties.apiCallTimeout()))
                .build();
        return client;
    }

    @Override public void put(String key, Path file, long size, String contentType, String sha256) {
        try {
            if(java.nio.file.Files.size(file)!=size) throw new StorageException(new IllegalStateException("Validated file length changed"));
            client().putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentLength(size)
                    .contentType(contentType).checksumSHA256(Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256)))
                    .metadata(Map.of("sha256", sha256)).build(), RequestBody.fromFile(file));
        } catch (java.io.IOException failure) { throw new StorageException(failure); }
        catch (RuntimeException failure) { throw sanitized(failure); }
    }
    @Override public StoredObject open(String key) {
        try {
            ResponseInputStream<GetObjectResponse> input = client().getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return new StoredObject() {
                @Override public InputStream stream() { return input; }
                @Override public long size() { return input.response().contentLength(); }
                // Abort instead of draining a download after HEAD, client disconnect, or an early failure.
                @Override public void close() { input.abort(); }
            };
        } catch (RuntimeException failure) { throw sanitized(failure); }
    }
    @Override public void delete(String key) {
        try { client().deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build()); }
        catch (RuntimeException failure) { throw sanitized(failure); }
    }
    private StorageException sanitized(RuntimeException failure) {
        return failure instanceof StorageException safe ? safe : new StorageException(failure);
    }
    @PreDestroy public synchronized void close() { if (client != null) client.close(); }
}
