package com.aiworkbench.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiworkbench.project.CreateProjectRequest;
import com.aiworkbench.project.ProjectResponse;
import com.aiworkbench.project.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostgreSqlPersistenceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WorkRecordService recordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsHistoricalRecordsWithSeparateOccurrenceAndCreationTimes() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("历史项目"));
        Instant occurredAt = Instant.parse("2026-09-18T01:30:00Z");

        WorkRecordResponse created = recordService.create(new CreateWorkRecordRequest(
                project.id(), "补记昨天完成的接口联调", occurredAt));

        assertThat(created.occurredAt()).isEqualTo(occurredAt);
        assertThat(created.createdAt()).isAfter(occurredAt);
        assertThat(recordService.list(LocalDate.of(2026, 9, 18)))
                .extracting(WorkRecordResponse::id)
                .contains(created.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM work_records WHERE id = ?", String.class, created.id()))
                .isEqualTo("补记昨天完成的接口联调");
    }

    @Test
    void usesAsiaShanghaiDayBoundariesRatherThanUtcBoundaries() {
        WorkRecordResponse beforeMidnight = recordService.create(new CreateWorkRecordRequest(
                null, "上海时间十八日", Instant.parse("2026-09-18T15:59:59Z")));
        WorkRecordResponse afterMidnight = recordService.create(new CreateWorkRecordRequest(
                null, "上海时间十九日", Instant.parse("2026-09-18T16:00:00Z")));

        assertThat(recordService.list(LocalDate.of(2026, 9, 18)))
                .extracting(WorkRecordResponse::id)
                .contains(beforeMidnight.id())
                .doesNotContain(afterMidnight.id());
        assertThat(recordService.list(LocalDate.of(2026, 9, 19)))
                .extracting(WorkRecordResponse::id)
                .contains(afterMidnight.id())
                .doesNotContain(beforeMidnight.id());
    }

    @Test
    void keepsHistoricalOwnershipButRejectsNewRecordsForArchivedProjects() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("即将归档"));
        WorkRecordResponse existing = recordService.create(new CreateWorkRecordRequest(
                project.id(), "归档前的工作", Instant.parse("2026-09-19T02:00:00Z")));

        projectService.archive(project.id());

        WorkRecordResponse reloaded = recordService.update(existing.id(), new UpdateWorkRecordRequest(
                project.id(), "归档后修改历史记录", existing.occurredAt()));
        assertThat(reloaded.project()).isNotNull();
        assertThat(reloaded.project().id()).isEqualTo(project.id());
        assertThat(reloaded.project().status()).isEqualTo("ARCHIVED");
        assertThat(reloaded.content()).isEqualTo("归档后修改历史记录");
        assertThatThrownBy(() -> recordService.create(new CreateWorkRecordRequest(
                project.id(), "归档后不允许新增", Instant.parse("2026-09-19T03:00:00Z"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("归档项目");
    }

    @Test
    void exposesValidationAndArchivedProjectConflictsThroughHttp() throws Exception {
        ProjectResponse project = projectService.create(new CreateProjectRequest("接口状态项目"));
        projectService.archive(project.id());

        mockMvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateWorkRecordRequest(
                                project.id(), "不能新增", Instant.parse("2026-09-19T03:00:00Z")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("归档项目不能用于新记录"));

        mockMvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":null,\"content\":\"   \",\"occurredAt\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void databaseEnforcesActiveNameUniqueness() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("Constraint Project"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO projects (id, name) VALUES (?, ?)", UUID.randomUUID(), project.name().toLowerCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databasePreventsDeletingProjectsReferencedByHistoricalRecords() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("外键约束项目"));
        WorkRecordResponse record = recordService.create(new CreateWorkRecordRequest(
                project.id(), "受外键保护", Instant.parse("2026-09-19T04:30:00Z")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT project_id FROM work_records WHERE id = ?", UUID.class, record.id()))
                .isEqualTo(project.id());
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM projects WHERE id = ?", project.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatesAndDeletesPersistedRecords() {
        WorkRecordResponse created = recordService.create(new CreateWorkRecordRequest(
                null, "初始内容", Instant.parse("2026-09-19T04:00:00Z")));

        WorkRecordResponse updated = recordService.update(created.id(), new UpdateWorkRecordRequest(
                null, "修改后的内容", Instant.parse("2026-09-18T04:00:00Z")));

        assertThat(updated.content()).isEqualTo("修改后的内容");
        assertThat(recordService.list(LocalDate.of(2026, 9, 18)))
                .extracting(WorkRecordResponse::id)
                .contains(created.id());

        recordService.delete(created.id());
        assertThatThrownBy(() -> recordService.get(created.id()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
