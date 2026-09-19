package com.aiworkbench.task;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest
class TaskMigrationIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesLegacyV1TasksThroughV3AndSupportsFreshSchemas() throws Exception {
        verifyLegacyUpgrade();
        verifyFreshMigration();
    }

    private void verifyLegacyUpgrade() throws Exception {
        String schema = schemaName("upgrade");
        try {
            migrate(schema, "1");
            inSchema(schema, jdbc -> jdbc.update(
                    "INSERT INTO todo_items (id, title) VALUES (?, ?)", UUID.randomUUID(), "legacy open task"));

            migrate(schema, null);

            inSchema(schema, jdbc -> {
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT status, notes, priority, version FROM todo_items WHERE title = ?",
                        "legacy open task");
                assertThat(row).containsEntry("status", "PENDING")
                        .containsEntry("notes", "")
                        .containsEntry("priority", "MEDIUM")
                        .containsEntry("version", 0L);
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
