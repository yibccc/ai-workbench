package com.aiworkbench.community;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.service.*;
import com.aiworkbench.storage.ObjectStorage;
import com.aiworkbench.support.OwnerTestContext;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AttachmentMigrationIntegrationTest {
    @Autowired PublishingService publishing;
    @Autowired AttachmentService attachments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;
    UUID a,b;
    @BeforeEach void setup() { a=account(); b=account(); }
    @AfterEach void cleanup() { for(String key:jdbc.queryForList("SELECT object_key FROM community_attachments WHERE owner_id IN (?,?)",String.class,a,b))storage.delete(key); CommunityTestData.remove(jdbc,a); CommunityTestData.remove(jdbc,b); SecurityContextHolder.clearContext(); }
    UUID account() { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,'USER')",id,"attachment-fk-"+id,"synthetic hash");return id; }
    OwnerPost post(UUID owner) { OwnerTestContext.use(owner); return publishing.create(new CreatePost(CommunityPostType.BLOG,null,"FK","","body")); }
    Published publish(OwnerPost post,long version,List<UUID> ids) { return publishing.publish(post.postId(),new PublishPost(UUID.randomUUID(),version,"MEMBERS",CommunityPostType.BLOG,null,"FK","","body",ids)); }
    @Test void actualCompositeForeignKeysRejectCrossOwnerPostAndRevisionReferences() {
        var pa=post(a); var file=attachments.upload(pa.postId(),0,UUID.randomUUID(),"one.md",new ByteArrayInputStream(new byte[]{'a'}));
        var ra=publish(pa,file.version(),List.of(file.attachment().id())); var otherA=post(a);
        var pb=post(b); var rb=publish(pb,0,List.of());
        assertThatThrownBy(()->jdbc.update("INSERT INTO community_draft_attachments(post_id,owner_id,attachment_id,position) VALUES (?,?,?,0)",pb.postId(),b,file.attachment().id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO community_draft_attachments(post_id,owner_id,attachment_id,position) VALUES (?,?,?,0)",otherA.postId(),a,file.attachment().id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO community_revision_attachments(revision_id,post_id,owner_id,attachment_id,position) VALUES (?,?,?,?,0)",rb.revisionId(),pa.postId(),a,file.attachment().id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM community_attachments WHERE id=?",file.attachment().id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_revision_attachments WHERE revision_id=?",Integer.class,ra.revisionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_revision_attachments WHERE revision_id=?",Integer.class,rb.revisionId())).isZero();
    }
}
