package com.aiworkbench.community;

import com.aiworkbench.dto.community.CommunityModels.PostDetail;
import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.mapper.CommunityPostMapper;
import com.aiworkbench.service.*;
import com.aiworkbench.storage.ObjectStorage;
import com.aiworkbench.support.OwnerTestContext;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AttachmentReadConsistencyIntegrationTest {
    @Autowired PublishingService publishing;
    @Autowired CommunityService community;
    @Autowired AttachmentService attachments;
    @Autowired ObjectStorage storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockitoSpyBean CommunityPostMapper posts;
    UUID owner;
    @BeforeEach void setup() { owner=UUID.randomUUID(); jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,?,?,'USER')",owner,"read-consistency-"+owner,"synthetic hash");OwnerTestContext.use(owner); }
    @AfterEach void cleanup() { reset(posts); for(String key:jdbc.queryForList("SELECT object_key FROM community_attachments WHERE owner_id=?",String.class,owner))storage.delete(key); CommunityTestData.remove(jdbc,owner);SecurityContextHolder.clearContext(); }
    @Test void concurrentPublicationCannotMixOldBodyWithNewOrEmptyAttachmentMetadata() throws Exception {
        var post=publishing.create(new CreatePost(CommunityPostType.BLOG,null,"coherence","","draft"));
        var f1=attachments.upload(post.postId(),0,UUID.randomUUID(),"f1.md",new ByteArrayInputStream(new byte[]{'1'}));
        var first=publish(post.postId(),f1.version(),f1.attachment().id(),"body F1");
        var f2=attachments.upload(post.postId(),first.version(),UUID.randomUUID(),"f2.md",new ByteArrayInputStream(new byte[]{'2'}));
        var selected=new CountDownLatch(1);var release=new CountDownLatch(1);
        CommunityPostMapper actualMapper=sqlSession.getMapper(CommunityPostMapper.class);
        doAnswer(call->{Object row=actualMapper.findPublished(post.postId());selected.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return row;}).when(posts).findPublished(post.postId());
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<PostDetail> reading=pool.submit(()->{OwnerTestContext.use(owner);try{return community.get(post.postId());}finally{SecurityContextHolder.clearContext();}});
            assertThat(selected.await(10,TimeUnit.SECONDS)).isTrue();
            publish(post.postId(),f2.version(),f2.attachment().id(),"body F2");release.countDown();
            var original=reading.get(10,TimeUnit.SECONDS);
            assertThat(original.bodyMarkdown()).isEqualTo("body F1");assertThat(original.attachments()).extracting(a->a.id()).containsExactly(f1.attachment().id());
            reset(posts);var current=community.get(post.postId());assertThat(current.bodyMarkdown()).isEqualTo("body F2");assertThat(current.attachments()).extracting(a->a.id()).containsExactly(f2.attachment().id());
        } finally { release.countDown();pool.shutdownNow(); }
    }
    Published publish(UUID post,long version,UUID id,String body) { return publishing.publish(post,new PublishPost(UUID.randomUUID(),version,"MEMBERS",CommunityPostType.BLOG,null,"coherence","",body,List.of(id))); }
}
