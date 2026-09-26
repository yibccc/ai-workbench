package com.aiworkbench.service.impl;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
class FocusUpgradeIntegrationTest {
    @Autowired DataSource dataSource;

    @Test void v14OwnedBusinessRowsUpgradeToV15WithoutChangingOldSnapshotsOrChecksums() throws Exception {
        String schema="focus_upgrade_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        UUID user=UUID.randomUUID(),other=UUID.randomUUID(),project=UUID.randomUUID(),task=UUID.randomUUID();
        UUID record=UUID.randomUUID(),report=UUID.randomUUID(),source=UUID.randomUUID();
        Instant occurred=Instant.parse("2052-04-09T04:00:00Z");
        try {
            migrate(schema,"14");
            Map<String,Integer> oldChecksums=inSchema(schema,jdbc->jdbc.query("SELECT version,checksum FROM flyway_schema_history " +
                    "WHERE version IS NOT NULL AND version::integer BETWEEN 1 AND 14 ORDER BY version::integer",
                    rs->{java.util.Map<String,Integer> result=new java.util.LinkedHashMap<>();
                        while(rs.next())result.put(rs.getString(1),rs.getInt(2));return result;}));
            assertThat(oldChecksums).hasSize(14);
            inSchema(schema,jdbc->{
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'old-owner','hash','USER')",user);
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'other-owner','hash','ADMIN')",other);
                jdbc.update("INSERT INTO projects(id,user_id,name) VALUES (?,?,'旧项目')",project,user);
                jdbc.update("INSERT INTO todo_items(id,user_id,project_id,title,status) VALUES (?,?,?,'旧待办','PENDING')",task,user,project);
                jdbc.update("INSERT INTO work_records(id,user_id,project_id,content,source,occurred_at) VALUES (?,?,?,'旧手工记录','MANUAL',?)",
                        record,user,project,Timestamp.from(occurred));
                jdbc.update("INSERT INTO reports(id,user_id,request_id,report_type,period_start,period_end,status,content) " +
                        "VALUES (?,?,?,'DAILY',?,?,'SUCCEEDED','旧报告正文')",report,user,UUID.randomUUID(),
                        LocalDate.of(2052,4,9),LocalDate.of(2052,4,9));
                jdbc.update("INSERT INTO report_sources(id,report_id,source_type,source_role,entity_id,content,source_status,source_time,snapshot) " +
                        "VALUES (?,?,'RECORD','DAILY_RECORD',?,'旧手工记录','MANUAL',?,'{\"source\":\"MANUAL\"}'::jsonb)",
                        source,report,record,Timestamp.from(occurred));
                return null;
            });
            migrate(schema,"15");
            inSchema(schema,jdbc->{
                Map<String,Integer> after=jdbc.query("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL " +
                        "AND version::integer BETWEEN 1 AND 14 ORDER BY version::integer",rs->{
                            java.util.Map<String,Integer> result=new java.util.LinkedHashMap<>();
                            while(rs.next())result.put(rs.getString(1),rs.getInt(2));return result;});
                assertThat(after).isEqualTo(oldChecksums);
                assertThat(jdbc.queryForObject("SELECT title FROM todo_items WHERE id=?",String.class,task)).isEqualTo("旧待办");
                assertThat(jdbc.queryForObject("SELECT source FROM work_records WHERE id=?",String.class,record)).isEqualTo("MANUAL");
                assertThat(jdbc.queryForObject("SELECT content FROM reports WHERE id=?",String.class,report)).isEqualTo("旧报告正文");
                assertThat(jdbc.queryForObject("SELECT snapshot::text FROM report_sources WHERE id=?",String.class,source))
                        .contains("\"MANUAL\"");
                assertThatThrownBy(()->jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,task_id,title,target_ms,interval_ms,zone_id,
                        phase,started_at,anchor_at,next_break_at_ms)
                    VALUES (?,?,?,?,?,60000,60000,'Asia/Shanghai','RUNNING',?,?,60000)
                    """,UUID.randomUUID(),other,UUID.randomUUID(),task,"跨账号关联",Timestamp.from(occurred),Timestamp.from(occurred)))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                return null;
            });
        } finally {try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){
            statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
    }
    private void migrate(String schema,String target){
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).createSchemas(true)
                .locations("classpath:db/migration").target(target).load().migrate();
    }
    private <T> T inSchema(String schema,java.util.function.Function<JdbcTemplate,T> action) throws Exception {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){
            String previous=connection.getSchema();statement.execute("SET search_path TO "+schema);
            try{return action.apply(new JdbcTemplate(new SingleConnectionDataSource(connection,true)));}
            finally{connection.setSchema(previous);}
        }
    }
}
