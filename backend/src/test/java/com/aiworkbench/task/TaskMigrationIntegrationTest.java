package com.aiworkbench.task;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TaskMigrationIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesLegacyV1TasksThroughLatestAndSupportsFreshSchemas() throws Exception {
        verifyLegacyUpgrade();
        verifyV5CaptureUpgrade();
        verifyFreshMigration();
    }

    private void verifyLegacyUpgrade() throws Exception {
        String schema = schemaName("upgrade");
        try {
            migrate(schema, "1");
            inSchema(schema, jdbc -> jdbc.update(
                    "INSERT INTO todo_items (id, title) VALUES (?, ?)", UUID.randomUUID(), "legacy open task"));
            inSchema(schema, jdbc -> jdbc.update(
                    "INSERT INTO todo_items (id, title, status, completed_at) VALUES (?, ?, 'DONE', CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), "legacy done task"));

            migrate(schema, null);

            inSchema(schema, jdbc -> {
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT status, notes, priority, version FROM todo_items WHERE title = ?",
                        "legacy open task");
                assertThat(row).containsEntry("status", "PENDING")
                        .containsEntry("notes", "")
                        .containsEntry("priority", "MEDIUM")
                        .containsEntry("version", 0L);
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM todo_items WHERE title = ?", String.class, "legacy done task"))
                        .isEqualTo("COMPLETED");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'todo_items' AND column_name = 'deleted_at'",
                        Integer.class, schema)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'capture_generated_items'",
                        Integer.class, schema)).isEqualTo(1);
            });
        } finally {
            dropSchema(schema);
        }
    }

    private void verifyFreshMigration() throws Exception {
        String schema = schemaName("fresh");
        try {
            migrate(schema, null);
            inSchema(schema, jdbc -> {
                jdbc.update("INSERT INTO todo_items (id, title) VALUES (?, ?)", UUID.randomUUID(), "fresh task");
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM todo_items WHERE title = ?", String.class, "fresh task"))
                        .isEqualTo("PENDING");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'capture_inputs' AND column_name = 'processing_token'",
                        Integer.class, schema)).isEqualTo(1);
            });
        } finally {
            dropSchema(schema);
        }
    }

    private void verifyV5CaptureUpgrade() throws Exception {
        String schema = schemaName("capture_upgrade");
        UUID inputId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        try {
            migrate(schema, "5");
            inSchema(schema, jdbc -> {
                jdbc.update("""
                        INSERT INTO capture_inputs
                            (id, content, source, captured_at, client_request_id, reference_at, zone_id,
                             status, completed_at)
                        VALUES (?, 'legacy capture', 'AI', CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP,
                                'Asia/Shanghai', 'SUCCEEDED', CURRENT_TIMESTAMP)
                        """, inputId, "legacy-capture-" + inputId);
                jdbc.update("""
                        INSERT INTO work_records (id, content, occurred_at, capture_input_id)
                        VALUES (?, 'legacy generated record', CURRENT_TIMESTAMP, ?)
                        """, recordId, inputId);
                jdbc.update("""
                        INSERT INTO todo_items (id, title, capture_input_id)
                        VALUES (?, 'legacy generated task', ?)
                        """, taskId, inputId);
            });

            migrate(schema, null);

            inSchema(schema, jdbc -> {
                assertThat(jdbc.queryForObject(
                        "SELECT revertible FROM capture_inputs WHERE id=?", Boolean.class, inputId)).isFalse();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM capture_generated_items WHERE input_id=?", Integer.class, inputId))
                        .isEqualTo(2);
                assertThat(jdbc.queryForList(
                        "SELECT initial_version FROM capture_generated_items WHERE input_id=?", Long.class, inputId))
                        .containsOnly(0L);
            });
        } finally {
            dropSchema(schema);
        }
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private void inSchema(String schema, SqlAction action) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            action.run(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        }
    }

    private void dropSchema(String schema) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private String schemaName(String prefix) {
        return "task_migration_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(JdbcTemplate jdbcTemplate) throws Exception;
    }
}
