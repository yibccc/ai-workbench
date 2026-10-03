package com.aiworkbench.community;

import com.aiworkbench.dto.publishing.PublishingModels.*;
import com.aiworkbench.dto.record.CreateWorkRecordRequest;
import com.aiworkbench.dto.record.UpdateWorkRecordRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.mapper.WorkRecordMapper;
import com.aiworkbench.service.*;
import com.aiworkbench.enums.CommunityPostType;
import com.aiworkbench.enums.WorkRecordSource;
import com.aiworkbench.support.OwnerTestContext;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class CommunitySourcesIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2093, 5, 9);
    private static final Instant START = DATE.atStartOfDay(ZONE).toInstant();
    @Autowired PublishingService publishing;
    @Autowired CommunityService community;
    @Autowired WorkRecordService records;
    @Autowired TaskService tasks;
    @Autowired WorkRecordMapper mapper;
    @Autowired FocusStore focus;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        OwnerTestContext.ensureAccounts(jdbc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
    }

    @Test void fullPagingAndCrossPageSelectionOnlyFreezeExplicitFieldsAndSelectedFocus() {
        var task = tasks.create(new CreateTaskRequest(null,"合并完成","不允许公开的任务备注",null,null));
        UUID rawFocus1 = focus(task.id(),START.plusSeconds(10),30_000);
        UUID rawFocus2 = focus(task.id(),START.plusSeconds(60),60_000);
        var complete = tasks.complete(task.id(),new CompleteTaskRequest(task.version(),"完成结果只出现一次"));
        jdbc.update("UPDATE work_records SET occurred_at=?,created_at=? WHERE id=?",Timestamp.from(START.plusSeconds(200)),
                Timestamp.from(START.plusSeconds(5)),complete.completionRecordId());
        UUID independent = focus(null,START.plusSeconds(300),120_000);
        jdbc.update("UPDATE work_records SET progress='未勾选的私有进展',created_at=? WHERE id=?",Timestamp.from(START.plusSeconds(6)),independent);
        UUID selectedManual = null;
        for (int i=0;i<5;i++) {
            var item = records.create(new CreateWorkRecordRequest(null,"手工成果"+i,START.plusSeconds(500)));
            jdbc.update("UPDATE work_records SET created_at=? WHERE id=?",Timestamp.from(START.plusSeconds(i)),item.id());
            if (i==0) selectedManual=item.id();
        }
        assertThat(publishing.sources(DATE,0,5).totalElements()).isEqualTo(7);
        assertThat(publishing.sources(DATE,0,5).items()).hasSize(5);
        assertThat(publishing.sources(DATE,1,5).items()).hasSize(2).extracting(Material::id).contains(selectedManual);
        var selections = List.of(new Selection(complete.completionRecordId(),List.of(MaterialField.CONTENT,MaterialField.COMPLETION_RESULT)),
                new Selection(selectedManual,List.of(MaterialField.CONTENT)));
        var noFocus = publishing.shareDraft(new ShareDraft(DATE,selections,false));
        assertThat(noFocus.draft().bodyMarkdown()).contains("完成待办：合并完成","完成结果只出现一次","手工成果0")
                .doesNotContain("专注投入","未勾选","任务备注","手工成果1","2093-05-09T");
        assertThat(community.page(null,OwnerTestContext.USER_ID,0,5).items()).isEmpty();
        var withFocus = publishing.shareDraft(new ShareDraft(DATE,selections,true));
        assertThat(withFocus.postId()).isNotEqualTo(noFocus.postId());
        assertThat(withFocus.draft().bodyMarkdown()).contains("所选素材专注投入：1 分 30 秒").doesNotContain("2 分");
        assertThat(withFocus.draft().bodyMarkdown().split("完成结果只出现一次",-1)).hasSize(2);
        String snapshots = jdbc.queryForObject("SELECT source_selection::text FROM community_post_drafts WHERE post_id=?",String.class,withFocus.postId());
        assertThat(snapshots).contains(rawFocus1.toString(),rawFocus2.toString()).doesNotContain(independent.toString());
        var published = publishing.publish(withFocus.postId(),new PublishPost(UUID.randomUUID(),0L,"MEMBERS",CommunityPostType.DAILY,
                DATE,withFocus.draft().title(),"",withFocus.draft().bodyMarkdown(),List.of()));
        var frozen = community.get(withFocus.postId());
        records.update(selectedManual,new UpdateWorkRecordRequest(null,"来源修改后",START.plusSeconds(500)));
        records.delete(selectedManual);
        assertThat(community.get(withFocus.postId())).isEqualTo(frozen);
        assertThat(published.revisionNo()).isEqualTo(1);
        assertThat(records.list(DATE)).hasSize(8); // raw focused rows remain; one manual deleted.
    }

    @Test void foreignWrongDayAbsorbedAndEmptySelectionsRejectWholeCreation() {
        var own = records.create(new CreateWorkRecordRequest(null,"本人当天",START));
        var wrongDay = records.create(new CreateWorkRecordRequest(null,"另一天",START.minusSeconds(1)));
        OwnerTestContext.use(OwnerTestContext.OTHER_ID);
        var foreign = records.create(new CreateWorkRecordRequest(null,"另一账号敏感内容",START));
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        int before = jdbc.queryForObject("SELECT count(*) FROM community_posts",Integer.class);
        CommunityPublishingIntegrationTest.assertNotFound(() -> publishing.shareDraft(new ShareDraft(DATE,List.of(
                new Selection(own.id(),List.of(MaterialField.CONTENT)),new Selection(foreign.id(),List.of(MaterialField.CONTENT))),false)));
        CommunityPublishingIntegrationTest.assertConflict(() -> publishing.shareDraft(new ShareDraft(DATE,
                List.of(new Selection(wrongDay.id(),List.of(MaterialField.CONTENT))),false)));
        CommunityPublishingIntegrationTest.assertBadRequest(() -> publishing.shareDraft(new ShareDraft(DATE,List.of(),false)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_posts",Integer.class)).isEqualTo(before);
        assertThat(publishing.sources(DATE.minusDays(1),0,5).items()).singleElement().extracting(Material::id).isEqualTo(wrongDay.id());
        assertThat(publishing.sources(DATE,0,5).items()).singleElement().extracting(Material::id).isEqualTo(own.id());
        var task = tasks.create(new CreateTaskRequest(null,"合并任务","",null,null));
        UUID rawFocus = focus(task.id(),START.plusSeconds(100),10_000);
        var completed = tasks.complete(task.id(),new CompleteTaskRequest(task.version(),"完成"));
        jdbc.update("UPDATE work_records SET occurred_at=? WHERE id=?",Timestamp.from(START.plusSeconds(200)),completed.completionRecordId());
        CommunityPublishingIntegrationTest.assertConflict(() -> publishing.shareDraft(new ShareDraft(DATE,
                List.of(new Selection(rawFocus,List.of(MaterialField.CONTENT))),true)));
        assertThat(publishing.sources(DATE,0,5).items()).extracting(Material::source).contains(WorkRecordSource.TASK_COMPLETION);
    }

    private UUID focus(UUID taskId,Instant start,long ms) {
        UUID session=UUID.randomUUID(), record=UUID.randomUUID();
        focus.insertSession(session,OwnerTestContext.USER_ID,UUID.randomUUID(),taskId,null,"投入",60_000,60_000,ZONE.getId(),start);
        Instant end=start.plusMillis(ms);
        jdbc.update("UPDATE focus_sessions SET phase='ENDED',ended_at=?,focus_ms=? WHERE id=?",Timestamp.from(end),ms,session);
        focus.insertFocusRecord(record,OwnerTestContext.USER_ID,null,taskId,session,"投入",DATE,ms,0,start,end);
        return record;
    }
}
