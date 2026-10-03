package com.aiworkbench.community;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.exception.AttachmentException;
import com.aiworkbench.service.*;
import com.aiworkbench.storage.ObjectStorage;
import com.aiworkbench.support.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AttachmentQuotaIntegrationTest {
    @Autowired PublishingService publishing;
    @Autowired AttachmentService service;
    @Autowired ObjectStorage storage;
    @Autowired JdbcTemplate jdbc;
    UUID owner;
    @BeforeEach void setup() { owner=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,'USER')",owner,"quota-"+owner,"synthetic hash"); OwnerTestContext.use(owner); }
    @AfterEach void cleanup() { for(String key:jdbc.queryForList("SELECT object_key FROM community_attachments WHERE owner_id=?",String.class,owner))storage.delete(key); CommunityTestData.remove(jdbc,owner); SecurityContextHolder.clearContext(); }
    @Test void exactTwentyMiBPdfFiveMiBPngAndFiftyMiBCurrentCollectionAreAllowed() throws Exception {
        UUID post=publishing.create(new CreatePost(CommunityPostType.BLOG,null,"quota","","body")).postId();
        long version=0; List<UUID> ids=new ArrayList<>();
        byte[] pdf=AttachmentTestFiles.pdfExact(20_971_520),png=AttachmentTestFiles.pngExact(5_242_880);
        for(int i=0;i<4;i++) {
            boolean isPdf=i<2; byte[] bytes=isPdf?pdf:png;
            var file=service.upload(post,version,UUID.randomUUID(),"exact."+(isPdf?"pdf":"png"),new ByteArrayInputStream(bytes));
            version=file.version(); ids.add(file.attachment().id()); assertThat(file.attachment().size()).isEqualTo(bytes.length);
            try(var object=service.download(post,file.attachment().id(),true).object()) {
                assertThat(object.size()).isEqualTo(bytes.length);
                assertThat(java.security.MessageDigest.getInstance("SHA-256").digest(object.stream().readAllBytes())).isEqualTo(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            }
        }
        assertThat(jdbc.queryForObject("SELECT sum(actual_size) FROM community_attachments WHERE post_id=?",Long.class,post)).isEqualTo(52_428_800);
        final long token=version;
        assertThatThrownBy(()->service.upload(post,token,UUID.randomUUID(),"one.md",new ByteArrayInputStream(new byte[]{'a'})))
                .isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.code()).isEqualTo("ATTACHMENT_QUOTA"));
        var pub=publishing.publish(post,new PublishPost(UUID.randomUUID(),version,"MEMBERS",CommunityPostType.BLOG,null,"quota","","body",ids));
        var saved=publishing.save(post,new SaveDraft(pub.version(),CommunityPostType.BLOG,null,"quota","","body",List.of()));
        assertThat(service.upload(post,saved.version(),UUID.randomUUID(),"new.md",new ByteArrayInputStream(new byte[]{'a'})).attachment().state()).isEqualTo("READY");
        assertThat(service.cleanup(post).results()).isEmpty();
    }
    @Test void imageAndPdfPlusOneAreRejectedBeforeReservationWithoutChangingBody() throws Exception {
        UUID post=publishing.create(new CreatePost(CommunityPostType.BLOG,null,"quota","","body")).postId();
        for(String type:List.of("pdf","png")) {
            byte[] bytes=type.equals("pdf")?AttachmentTestFiles.pdfExact(20_971_521):AttachmentTestFiles.pngExact(5_242_881);
            assertThatThrownBy(()->service.upload(post,0,UUID.randomUUID(),"over."+type,new ByteArrayInputStream(bytes)))
                    .isInstanceOfSatisfying(AttachmentException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(413));
        }
        assertThat(publishing.get(post).version()).isZero(); assertThat(publishing.get(post).draft().bodyMarkdown()).isEqualTo("body");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post)).isZero();
    }
}
