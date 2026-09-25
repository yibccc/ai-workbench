package com.aiworkbench.web;

import com.aiworkbench.common.PageQueries;
import com.aiworkbench.mapper.ReportMapper;
import com.aiworkbench.service.ReportService;
import com.aiworkbench.support.OwnerTestContext;
import jakarta.servlet.http.Cookie;
import com.github.pagehelper.PageHelper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class PaginationHttpIntegrationTest {
    private UUID reportOwnerId() { return OwnerTestContext.USER_ID; }
    private static final LocalDate DATE = LocalDate.of(2088, 4, 12);
    private final String prefix = "page-" + UUID.randomUUID();
    private final List<UUID> projectIds = new ArrayList<>();
    private final List<UUID> recordIds = new ArrayList<>();
    private final List<UUID> taskIds = new ArrayList<>();
    private final List<UUID> reportIds = new ArrayList<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportMapper reportMapper;
    @Autowired ReportService reportService;
    private Cookie ownerSession;

    @BeforeEach
    void seed() {
        OwnerTestContext.ensureAccounts(jdbc);
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        try { ownerSession = OwnerTestContext.login(mvc); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
        OwnerTestContext.use(OwnerTestContext.USER_ID);
        Instant base = Instant.parse("2088-04-12T01:00:00Z");
        for (int index = 0; index < 21; index++) {
            Instant created = base.plusSeconds(index);
            UUID projectId = UUID.randomUUID(); projectIds.add(projectId);
            jdbc.update("INSERT INTO projects(id,user_id,name,created_at,updated_at) VALUES (?,?,?,?,?)",
                    projectId, OwnerTestContext.USER_ID, prefix + "-project-" + index, Timestamp.from(created), Timestamp.from(created));
            UUID recordId = UUID.randomUUID(); recordIds.add(recordId);
            jdbc.update("INSERT INTO work_records(id,user_id,content,occurred_at,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                    recordId, OwnerTestContext.USER_ID, prefix + "-record-" + index, Timestamp.from(base), Timestamp.from(created), Timestamp.from(created));
            UUID taskId = UUID.randomUUID(); taskIds.add(taskId);
            jdbc.update("INSERT INTO todo_items(id,user_id,project_id,title,status,priority,notes,version,created_at,updated_at) VALUES (?,?,?,?,'PENDING','MEDIUM','',0,?,?)",
                    taskId, OwnerTestContext.USER_ID, projectIds.get(0), prefix + "-task-" + index,
                    Timestamp.from(created), Timestamp.from(created));
            UUID reportId = UUID.randomUUID(); reportIds.add(reportId);
            jdbc.update("""
                    INSERT INTO reports(id,user_id,request_id,report_type,period_start,period_end,status,content,created_at,updated_at)
                    VALUES (?,?,?,'DAILY',?,?,'SUCCEEDED',?, ?, ?)
                    """, reportId, OwnerTestContext.USER_ID, UUID.randomUUID(), DATE, DATE, prefix + "-report-" + index,
                    Timestamp.from(created), Timestamp.from(created));
        }
        UUID reportId = reportIds.get(0);
        for (int index = 0; index < 21; index++) {
            jdbc.update("""
                    INSERT INTO report_sources(id,report_id,source_type,source_role,entity_id,content,source_time,snapshot)
                    VALUES (?,?,'RECORD','DAILY_RECORD',?,?,?, '{}'::jsonb)
                    """, UUID.randomUUID(), reportId, UUID.randomUUID(), prefix + "-source-" + index,
                    Timestamp.from(base.plusSeconds(index)));
        }
        jdbc.update("UPDATE reports SET source_count=21 WHERE id=?", reportId);
    }

    @AfterEach
    void cleanup() {
        reportIds.forEach(id -> jdbc.update("DELETE FROM report_sources WHERE report_id=?", id));
        reportIds.forEach(id -> jdbc.update("DELETE FROM reports WHERE id=?", id));
        recordIds.forEach(id -> jdbc.update("DELETE FROM work_records WHERE id=?", id));
        taskIds.forEach(id -> jdbc.update("DELETE FROM todo_items WHERE id=?", id));
        projectIds.forEach(id -> jdbc.update("DELETE FROM projects WHERE id=?", id));
        OwnerTestContext.removeBusinessData(jdbc);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        try { return mvc.perform(OwnerTestContext.authenticated(request, ownerSession)); }
        finally { OwnerTestContext.use(OwnerTestContext.USER_ID); }
    }

    @Test
    void pageScopeEndsBeforeMappingAndFailureCannotLeakIntoFullSourceQueries() {
        UUID id = reportIds.get(0);
        UUID userId = reportOwnerId();
        var page = PageQueries.select(1, 10, () -> reportMapper.findSourcePage(userId, id), row -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            assertThat(reportMapper.findSources(userId, id)).hasSize(21);
            return row.id();
        });
        assertThat(page.items()).hasSize(10);
        assertThat(page.totalElements()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(PageHelper.getLocalPage()).isNull();
        assertThat(reportService.get(id).sources()).hasSize(21);

        assertThatThrownBy(() -> PageQueries.select(0, 10, () -> {
            assertThat(PageHelper.getLocalPage()).isNotNull();
            throw new IllegalStateException("query failed before interception");
        }, value -> value)).isInstanceOf(IllegalStateException.class);
        assertThat(PageHelper.getLocalPage()).isNull();
        assertThat(reportMapper.findSources(userId, id)).hasSize(21);
        assertThat(reportService.sourcePage(id, 2, 10).items()).hasSize(1);
        assertThat(reportService.sourcePage(id, 9, 10).items()).isEmpty();
    }

    @Test
    void pagedRoutesKeepCountsFiltersOrderingAndStaticMappings() throws Exception {
        perform(get("/api/projects/page").param("q", prefix).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].name").value(prefix + "-project-15"));
        perform(get("/api/records/page").param("date", DATE.toString()).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].content").value(prefix + "-record-15"));
        perform(get("/api/tasks/page").param("status", "PENDING")
                        .param("projectId", projectIds.get(0).toString()).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].title").value(prefix + "-task-15"));
        perform(get("/api/reports/page").param("reportType", "DAILY")
                        .param("date", DATE.toString()).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.totalPages").value(5));
        perform(get("/api/reports/{id}/sources/page", reportIds.get(0)).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].content").value(prefix + "-source-5"));
        perform(get("/api/projects/page").param("q", prefix).param("page", "0").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].name").value(prefix + "-project-20"));
        perform(get("/api/records/page").param("date", DATE.toString()).param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.items[0].content").value(prefix + "-record-20"));
        perform(get("/api/tasks/page").param("status", "PENDING")
                        .param("projectId", projectIds.get(0).toString()).param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.items[0].title").value(prefix + "-task-20"));
        perform(get("/api/reports/page").param("reportType", "DAILY")
                        .param("date", DATE.toString()).param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.items", hasSize(10))).andExpect(jsonPath("$.items[0].sources", hasSize(0)));
        perform(get("/api/reports/{id}/sources/page", reportIds.get(0)).param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.items", hasSize(10))).andExpect(jsonPath("$.items[0].content").value(prefix + "-source-10"));
        perform(get("/api/projects/page").param("q", prefix).param("page", "99").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(99))
                .andExpect(jsonPath("$.items", hasSize(0)));
        perform(get("/api/projects/page").param("q", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(20)));
        perform(get("/api/projects/page").param("q", prefix).param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(21)));
        perform(get("/api/projects/page").param("size", "11")).andExpect(status().isBadRequest());
        perform(get("/api/tasks/page").param("page", "-1")).andExpect(status().isBadRequest());
        perform(get("/api/tasks/page").param("page", "2147483647").param("size", "50"))
                .andExpect(status().isBadRequest());
        perform(get("/api/projects").param("includeArchived", "false"))
                .andExpect(status().isOk());
    }
}
