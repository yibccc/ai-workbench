package com.aiworkbench.storage;

import com.aiworkbench.config.StorageProperties;
import java.nio.file.*;
import java.security.MessageDigest;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class RustFsStorageIntegrationTest {
    @Autowired ObjectStorage storage;
    @Autowired StorageProperties properties;
    @TempDir Path dir;
    @Test void sdkKnownLengthShaChecksumPrivateReadsAndDeleteUseOriginalBytes() throws Exception {
        String key="community/attachments/"+UUID.randomUUID();
        Path path=dir.resolve("sdk-original.bin"); byte[] bytes=new byte[65_537]; new Random(42).nextBytes(bytes); Files.write(path,bytes);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try {
            storage.put(key,path,bytes.length,"application/octet-stream",sha);
            try(var object=storage.open(key)) { assertThat(object.size()).isEqualTo(bytes.length); assertThat(object.stream().readAllBytes()).isEqualTo(bytes); }
            for(String method:List.of("GET","HEAD")) {
                var request=HttpRequest.newBuilder(URI.create(properties.endpoint()+"/"+properties.bucket()+"/"+key)).method(method,HttpRequest.BodyPublishers.noBody()).build();
                assertThat(HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
            }
            assertThatThrownBy(()->storage.put("community/attachments/"+UUID.randomUUID(),path,bytes.length,"application/octet-stream","00".repeat(32)))
                    .isInstanceOf(StorageException.class);
        } finally { storage.delete(key); }
        assertThatThrownBy(()->storage.open(key)).isInstanceOf(StorageException.class);
    }
}
