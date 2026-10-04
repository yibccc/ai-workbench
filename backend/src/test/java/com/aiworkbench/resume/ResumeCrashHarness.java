package com.aiworkbench.resume;

import com.aiworkbench.WorkbenchApplication;
import com.aiworkbench.service.ResumeService;
import com.aiworkbench.service.impl.ResumePersistenceService;
import com.aiworkbench.storage.*;
import com.aiworkbench.support.OwnerTestContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Test-only real JVM synchronization point; no HTTP test endpoint or production profile. */
public final class ResumeCrashHarness {
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException();
        UUID owner=UUID.fromString(args[1]); Path marker=Path.of(args[2]);
        try(var context=new SpringApplicationBuilder(WorkbenchApplication.class)
                .run("--spring.profiles.active=test","--server.port=0")) {
            var persistence=context.getBean(ResumePersistenceService.class);
            if(args[0].equals("reserve")) {
                try(var file=context.getBean(AttachmentValidator.class).stage("crash.md",new ByteArrayInputStream("crash original".getBytes(StandardCharsets.UTF_8)))) {
                    var reservation=persistence.reserve(owner,1,UUID.randomUUID(),"c".repeat(64),file);
                    var row=reservation.object();
                    context.getBean(ObjectStorage.class).put(row.storageKey(),file.path(),file.size(),file.contentType(),file.sha256());
                    Files.writeString(marker,row.id().toString(),StandardCharsets.UTF_8);
                    Thread.sleep(600000); // Parent forcibly terminates after verifying the committed reservation.
                }
            } else if(args[0].equals("recover")) {
                OwnerTestContext.use(owner); persistence.recover(owner); context.getBean(ResumeService.class).cleanup();
                Files.writeString(marker,"RECOVERED",StandardCharsets.UTF_8);
            } else throw new IllegalArgumentException();
        }
    }
}
