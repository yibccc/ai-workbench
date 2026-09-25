package com.aiworkbench.report;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReportMigrationIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void upgradesLegacyReportsAndAllowsMultipleDailyVersions() throws Exception {
        String schema = schemaName("legacy");
        UUID legacyId = UUID.randomUUID();
        try {
            migrate(schema, "1");
            inSchema(schema, jdbc -> jdbc.update("""
                    INSERT INTO reports(id, report_type, period_start, period_end, content)
                    VALUES (?, 'DAILY', DATE '2042-03-04', DATE '2042-03-04', '旧日报正文')
                    """, legacyId));

            migrate(schema, "13");

            inSchema(schema, jdbc -> {
                Map<String, Object> legacy = jdbc.queryForMap("""
                        SELECT request_id, status, version, zone_id, content
                        FROM reports WHERE id=?
                        """, legacyId);
                assertThat(legacy.get("request_id")).isNotNull();
                assertThat(legacy).containsEntry("status", "SUCCEEDED")
                        .containsEntry("version", 0L)
                        .containsEntry("zone_id", "Asia/Shanghai")
                        .containsEntry("content", "旧日报正文");
                jdbc.update("""
                        INSERT INTO reports(id, request_id, report_type, period_start, period_end, status, content)
                        VALUES (?, ?, 'DAILY', DATE '2042-03-04', DATE '2042-03-04', 'SUCCEEDED', '新版本')
                        """, UUID.randomUUID(), UUID.randomUUID());
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM reports
                        WHERE report_type='DAILY' AND period_start=DATE '2042-03-04'
                        """, Integer.class)).isEqualTo(2);
            });
            assertThatThrownBy(() -> migrate(schema, null)).hasMessageContaining("V14 requires an empty business database");
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void migratesFreshSchemaThroughDailyReportVersion() throws Exception {
        String schema = schemaName("fresh");
        try {
            migrate(schema, null);
            inSchema(schema, jdbc -> {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema=? AND table_name='report_sources'
                        """, Integer.class, schema)).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema=? AND table_name='reports' AND column_name='processing_token'
                        """, Integer.class, schema)).isEqualTo(1);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema=? AND table_name='reports'
                          AND column_name IN ('error_code','error_stage','source_count')
                        """, Integer.class, schema)).isEqualTo(3);
            });
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void upgradesV8SourcesAndReportsToWeeklyVersionModel() throws Exception {
        String schema = schemaName("v8");
        UUID reportId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        try {
            migrate(schema, "8");
            inSchema(schema, jdbc -> {
                jdbc.update("""
                        INSERT INTO reports(id,request_id,report_type,period_start,period_end,status,content)
                        VALUES (?,?, 'DAILY', DATE '2043-01-02', DATE '2043-01-02','SUCCEEDED','旧正文')
                        """, reportId, UUID.randomUUID());
                jdbc.update("""
                        INSERT INTO report_sources(id,report_id,source_type,entity_id,content,source_time)
                        VALUES (?,?,'RECORD',?,'旧来源',TIMESTAMPTZ '2043-01-02 01:00:00Z')
                        """, sourceId, reportId, UUID.randomUUID());
            });

            migrate(schema, "13");

            inSchema(schema, jdbc -> {
                assertThat(jdbc.queryForObject("SELECT source_role FROM report_sources WHERE id=?", String.class, sourceId))
                        .isEqualTo("DAILY_RECORD");
                Map<String, Object> report = jdbc.queryForMap("""
                        SELECT manual_additions, previous_report_id, manual_edited_at,
                               error_code, error_stage, source_count
                        FROM reports WHERE id=?
                        """, reportId);
                assertThat(report).containsEntry("manual_additions", "")
                        .containsEntry("previous_report_id", null)
                        .containsEntry("manual_edited_at", null)
                        .containsEntry("error_code", null)
                        .containsEntry("error_stage", null)
                        .containsEntry("source_count", 1);
            });
            assertThatThrownBy(() -> migrate(schema, null)).hasMessageContaining("V14 requires an empty business database");
        } finally {
            dropSchema(schema);
        }
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .createSchemas(true).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
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
        return "report_migration_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    @FunctionalInterface
    private interface SqlAction { void run(JdbcTemplate jdbcTemplate) throws Exception; }
}
