package com.aiworkbench.task;

import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.dto.project.ProjectResponse;
import com.aiworkbench.dto.task.CompleteTaskRequest;
import com.aiworkbench.dto.task.CreateTaskRequest;
import com.aiworkbench.dto.task.TaskResponse;
import com.aiworkbench.dto.task.UpdateTaskRequest;
import com.aiworkbench.enums.TaskDueFilter;
import com.aiworkbench.enums.TaskPriority;
import com.aiworkbench.enums.TaskStatus;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskManagementIntegrationTest {
    @Autowired TaskService taskService;
    @Autowired ProjectService projectService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void appliesDefaultsAndPersistsTasksWithoutInventingDueDates() {
        TaskResponse created = taskService.create(new CreateTaskRequest(null, "  整理技术方案  ", null, null, null));

        assertThat(created.title()).isEqualTo("整理技术方案");
        assertThat(created.notes()).isEmpty();
        assertThat(created.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(created.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(created.dueAt()).isNull();
        assertThat(created.project()).isNull();
        assertThat(created.version()).isZero();
        assertThat(taskService.get(created.id())).isEqualTo(created);
    }

    @Test
    void filtersByStatusProjectPriorityDueDateAndUnassigned() {
        ProjectResponse project = projectService.create(new CreateProjectRequest("筛选项目 " + UUID.randomUUID()));
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zoneId);
        Instant todayDueAt = today.atStartOfDay(zoneId).plusHours(12).toInstant();
        Instant upcomingDueAt = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        TaskResponse overdueHigh = taskService.create(new CreateTaskRequest(project.id(), "逾期高优先级", "", Instant.parse("2020-01-01T00:00:00Z"), TaskPriority.HIGH));
        TaskResponse dueToday = taskService.create(new CreateTaskRequest(project.id(), "今天到期", "", todayDueAt, TaskPriority.MEDIUM));
        TaskResponse upcoming = taskService.create(new CreateTaskRequest(project.id(), "之后到期", "", upcomingDueAt, TaskPriority.LOW));
        TaskResponse unassigned = taskService.create(new CreateTaskRequest(null, "未分类无期限", "", null, TaskPriority.LOW));
        taskService.complete(unassigned.id(), new CompleteTaskRequest(unassigned.version(), ""));

        assertThat(taskService.list(TaskStatus.PENDING, project.id(), false, TaskPriority.HIGH, TaskDueFilter.OVERDUE))
                .extracting(TaskResponse::id).containsExactly(overdueHigh.id());
        assertThat(taskService.list(TaskStatus.COMPLETED, null, true, TaskPriority.LOW, TaskDueFilter.NONE))
                .extracting(TaskResponse::id).containsExactly(unassigned.id());
        assertThat(taskService.list(TaskStatus.PENDING, project.id(), false, null, TaskDueFilter.TODAY))
                .extracting(TaskResponse::id).containsExactly(dueToday.id());
        assertThat(taskService.list(TaskStatus.PENDING, project.id(), false, TaskPriority.LOW, TaskDueFilter.UPCOMING))
                .extracting(TaskResponse::id).containsExactly(upcoming.id());
    }

    @Test
    void retainsArchivedHistoryButRejectsNewOrChangedArchivedAssociations() {
        ProjectResponse archived = projectService.create(new CreateProjectRequest("归档待办项目 " + UUID.randomUUID()));
        TaskResponse historical = taskService.create(new CreateTaskRequest(archived.id(), "归档前待办", "", null, null));
        projectService.archive(archived.id());

        TaskResponse updated = taskService.update(historical.id(), new UpdateTaskRequest(
                archived.id(), "保留历史归属", "已归档", null, TaskPriority.MEDIUM, historical.version()));
        assertThat(updated.project().status()).isEqualTo("ARCHIVED");
        assertThatThrownBy(() -> taskService.create(new CreateTaskRequest(
                archived.id(), "不允许新建", "", null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("归档项目");

        TaskResponse unassigned = taskService.create(new CreateTaskRequest(null, "未分类", "", null, null));
        assertThatThrownBy(() -> taskService.update(unassigned.id(), new UpdateTaskRequest(
                archived.id(), "不能改绑", "", null, TaskPriority.MEDIUM, unassigned.version())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("归档项目");
    }

    @Test
    void rejectsStaleUpdatesAndDeletesWithoutSilentOverwrite() throws Exception {
        TaskResponse created = taskService.create(new CreateTaskRequest(null, "并发编辑", "初始", null, null));
        TaskResponse updated = taskService.update(created.id(), new UpdateTaskRequest(
                null, "第一次保存", "最新", null, TaskPriority.HIGH, created.version()));
        assertThat(updated.version()).isEqualTo(1);

        UpdateTaskRequest stale = new UpdateTaskRequest(
                null, "过期保存", "旧数据", null, TaskPriority.LOW, created.version());
        mockMvc.perform(put("/api/tasks/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("待办已被其他操作修改，请刷新后重试"));
        assertThat(taskService.get(created.id()).title()).isEqualTo("第一次保存");
        mockMvc.perform(delete("/api/tasks/{id}", created.id()).param("version", String.valueOf(created.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("待办已被其他操作修改，请刷新后重试"));
    }

    @Test
    void rejectsContradictoryAndInvalidFilterParametersWithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("projectId", UUID.randomUUID().toString())
                        .param("unassigned", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("未分类筛选不能与项目筛选同时使用"));

        mockMvc.perform(get("/api/tasks").param("priority", "URGENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("请求参数无效"));
    }

    @Test
    void updatesReloadsAndDeletesWithCurrentVersion() {
        TaskResponse created = taskService.create(new CreateTaskRequest(
                null, "刷新前", "", Instant.parse("2030-01-01T00:00:00Z"), TaskPriority.LOW));
        TaskResponse updated = taskService.update(created.id(), new UpdateTaskRequest(
                null, "刷新后", "持久化备注", null, TaskPriority.HIGH, created.version()));

        TaskResponse reloaded = taskService.get(created.id());
        assertThat(reloaded.title()).isEqualTo("刷新后");
        assertThat(reloaded.notes()).isEqualTo("持久化备注");
        assertThat(reloaded.dueAt()).isNull();
        assertThat(reloaded.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM todo_items WHERE id = ?", Long.class, created.id()))
                .isEqualTo(1L);

        taskService.delete(created.id(), updated.version());
        assertThatThrownBy(() -> taskService.get(created.id()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("不存在");
    }
}
