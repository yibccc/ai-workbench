package com.aiworkbench.service.impl;

import com.aiworkbench.config.mybatis.UuidTypeHandler;
import com.aiworkbench.dto.focus.FocusModels.Checkpoint;
import com.aiworkbench.dto.focus.FocusModels.Version;
import com.aiworkbench.events.WorkbenchEventHub;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.support.OwnerTestContext;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FocusUpgradeIntegrationTest {
    @Autowired DataSource dataSource;

    @Test void v15PendingSessionsBecomeContinuousWithoutSettlingOldPendingIntervals() throws Exception {
        String schema="focus_upgrade_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        UUID user=UUID.randomUUID(),pausedOwner=UUID.randomUUID(),running=UUID.randomUUID(),paused=UUID.randomUUID();
        Instant start=Instant.parse("2052-04-09T04:00:00Z"),pendingStart=start.plusSeconds(120),
                pendingEnd=pendingStart.plusSeconds(300);
        try {
            migrate(schema,"15");
            Integer v15Checksum=inSchema(schema,jdbc->jdbc.queryForObject(
                    "SELECT checksum FROM flyway_schema_history WHERE version='15'",Integer.class));
            inSchema(schema,jdbc->{
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'v15-owner','hash','USER')",user);
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'v15-paused','hash','USER')",pausedOwner);
                jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,title,target_ms,interval_ms,zone_id,phase,
                        started_at,anchor_at,focus_ms,pending_start,pending_end,resume_phase,next_break_at_ms)
                    VALUES (?,?,?,'旧失联',1500000,600000,'Asia/Shanghai','RECOVERY_REQUIRED',?,?,?,?,?,'RUNNING',600000)
                    """,running,user,UUID.randomUUID(),Timestamp.from(start),Timestamp.from(pendingEnd),120_000,
                        Timestamp.from(pendingStart),Timestamp.from(pendingEnd));
                jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,title,target_ms,interval_ms,zone_id,phase,
                        started_at,anchor_at,focus_ms,pending_start,pending_end,resume_phase,break_remaining_ms,next_break_at_ms)
                    VALUES (?,?,?,'暂停失联',1500000,600000,'Asia/Shanghai','RECOVERY_REQUIRED',?,?,?,?,?,'PAUSED',10000,600000)
                    """,paused,pausedOwner,UUID.randomUUID(),Timestamp.from(start),Timestamp.from(pendingEnd),600_000,
                        Timestamp.from(pendingStart),Timestamp.from(pendingEnd));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) " +
                        "VALUES (?,?,1,'PENDING',?,?,false)",running,user,Timestamp.from(pendingStart),Timestamp.from(pendingEnd));
                return null;
            });
            migrate(schema,"16");
            inSchema(schema,jdbc->{
                assertThat(jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='15'",Integer.class))
                        .isEqualTo(v15Checksum);
                assertThat(jdbc.queryForObject("SELECT phase FROM focus_sessions WHERE id=?",String.class,running))
                        .isEqualTo("RUNNING");
                assertThat(jdbc.queryForObject("SELECT anchor_at FROM focus_sessions WHERE id=?",Timestamp.class,running).toInstant())
                        .isEqualTo(pendingStart);
                assertThat(jdbc.queryForObject("SELECT phase FROM focus_sessions WHERE id=?",String.class,paused))
                        .isEqualTo("PAUSED");
                assertThat(jdbc.queryForObject("SELECT resume_phase FROM focus_sessions WHERE id=?",String.class,paused))
                        .isEqualTo("MICRO_BREAK");
                assertThat(jdbc.queryForObject("SELECT confirmed FROM focus_intervals WHERE session_id=?",Boolean.class,running))
                        .isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? " +
                        "AND table_name='focus_sessions' AND column_name IN ('pending_start','pending_end')",Integer.class,schema))
                        .isZero();
                assertThatThrownBy(()->jdbc.update("UPDATE focus_sessions SET phase='RECOVERY_REQUIRED' WHERE id=?",running))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                return null;
            });
        } finally {try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){
            statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
    }

    @Test void upgradedRecoveryRowsContinueThroughNewServiceWithoutLosingOrDuplicatingTime() throws Exception {
        String schema="focus_upgrade_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        UUID runningOwner=UUID.randomUUID(),pausedOwner=UUID.randomUUID(),breakOwner=UUID.randomUUID();
        UUID running=UUID.randomUUID(),paused=UUID.randomUUID(),inBreak=UUID.randomUUID();
        Instant runningStart=Instant.parse("2052-04-09T15:59:00Z");
        Instant runningPending=runningStart.plusSeconds(120),runningEnd=runningPending.plusSeconds(300);
        Instant localStart=Instant.parse("2052-04-09T04:00:00Z");
        Instant pausedPending=localStart.plusSeconds(600),pausedEnd=pausedPending.plusSeconds(300);
        Instant breakPending=localStart.plusSeconds(605),breakEnd=breakPending.plusSeconds(300);
        try {
            migrate(schema,"15");
            inSchema(schema,jdbc->{
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'upgrade-running','hash','USER')",runningOwner);
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'upgrade-paused','hash','USER')",pausedOwner);
                jdbc.update("INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'upgrade-break','hash','USER')",breakOwner);
                jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,title,target_ms,interval_ms,zone_id,phase,
                        started_at,anchor_at,focus_ms,pending_start,pending_end,resume_phase,next_break_at_ms)
                    VALUES (?,?,?,'升级运行',600000,600000,'Asia/Shanghai','RECOVERY_REQUIRED',?,?,?,?,?,'RUNNING',600000)
                    """,running,runningOwner,UUID.randomUUID(),Timestamp.from(runningStart),Timestamp.from(runningEnd),120_000,
                        Timestamp.from(runningPending),Timestamp.from(runningEnd));
                jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,title,target_ms,interval_ms,zone_id,phase,
                        started_at,anchor_at,focus_ms,pending_start,pending_end,resume_phase,next_break_at_ms)
                    VALUES (?,?,?,'升级暂停',1500000,600000,'Asia/Shanghai','RECOVERY_REQUIRED',?,?,?,?,?,'PAUSED',600000)
                    """,paused,pausedOwner,UUID.randomUUID(),Timestamp.from(localStart),Timestamp.from(pausedEnd),600_000,
                        Timestamp.from(pausedPending),Timestamp.from(pausedEnd));
                jdbc.update("""
                    INSERT INTO focus_sessions(id,user_id,request_id,title,target_ms,interval_ms,zone_id,phase,
                        started_at,anchor_at,focus_ms,break_ms,pending_start,pending_end,resume_phase,break_remaining_ms,next_break_at_ms,reminder_ordinal)
                    VALUES (?,?,?,'升级微休息',1500000,600000,'Asia/Shanghai','RECOVERY_REQUIRED',?,?,?,?,?,?,'MICRO_BREAK',10000,600000,1)
                    """,inBreak,breakOwner,UUID.randomUUID(),Timestamp.from(localStart),Timestamp.from(breakEnd),600_000,5_000,
                        Timestamp.from(breakPending),Timestamp.from(breakEnd));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,1,'FOCUS',?,?,true)",
                        running,runningOwner,Timestamp.from(runningStart),Timestamp.from(runningPending));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,2,'PENDING',?,?,false)",
                        running,runningOwner,Timestamp.from(runningPending),Timestamp.from(runningEnd));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,1,'FOCUS',?,?,true)",
                        paused,pausedOwner,Timestamp.from(localStart),Timestamp.from(pausedPending));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,2,'PENDING',?,?,false)",
                        paused,pausedOwner,Timestamp.from(pausedPending),Timestamp.from(pausedEnd));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,1,'FOCUS',?,?,true)",
                        inBreak,breakOwner,Timestamp.from(localStart),Timestamp.from(localStart.plusSeconds(600)));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,2,'BREAK',?,?,true)",
                        inBreak,breakOwner,Timestamp.from(localStart.plusSeconds(600)),Timestamp.from(breakPending));
                jdbc.update("INSERT INTO focus_intervals(session_id,user_id,ordinal,kind,start_at,end_at,confirmed) VALUES (?,?,3,'PENDING',?,?,false)",
                        inBreak,breakOwner,Timestamp.from(breakPending),Timestamp.from(breakEnd));
                return null;
            });
            migrate(schema,"16");
            withMigratedFocusStore(schema,(store,jdbc)->{
                var events=mock(WorkbenchEventHub.class);
                var runningClock=new FocusIntegrationTest.MutableClock(runningEnd);
                var runningService=new FocusServiceImpl(store,null,null,null,events,ZoneId.of("Asia/Shanghai"),runningClock);
                OwnerTestContext.use(runningOwner);
                var run=runningService.get(running);
                run=runningService.checkpoint(running,new Checkpoint(run.version(),null,null));
                assertThat(run.focusMs()).isEqualTo(420_000);
                runningClock.advance(Duration.ofSeconds(300));
                run=runningService.checkpoint(running,new Checkpoint(run.version(),null,null));
                assertThat(run.phase()).isEqualTo("ENDED");
                assertThat(run.focusMs()).isEqualTo(600_000);
                assertThat(run.endedAt()).isEqualTo(runningEnd.plusSeconds(180));
                assertThat(jdbc.queryForList("SELECT business_date,focus_ms,break_ms FROM work_records WHERE focus_session_id=? ORDER BY business_date",running))
                        .hasSize(2).extracting(row->((Number)row.get("focus_ms")).longValue()).containsExactly(60_000L,540_000L);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM focus_intervals WHERE session_id=? AND kind='PENDING' AND NOT confirmed",Integer.class,running))
                        .isEqualTo(1);

                var pausedClock=new FocusIntegrationTest.MutableClock(pausedEnd.plusSeconds(60));
                var pausedService=new FocusServiceImpl(store,null,null,null,events,ZoneId.of("Asia/Shanghai"),pausedClock);
                OwnerTestContext.use(pausedOwner);
                var hold=pausedService.get(paused);
                hold=pausedService.checkpoint(paused,new Checkpoint(hold.version(),null,null));
                assertThat(hold.phase()).isEqualTo("PAUSED");
                assertThat(hold.focusMs()).isEqualTo(600_000);
                assertThat(hold.pauseMs()).isEqualTo(360_000);
                hold=pausedService.end(paused,new Version(hold.version()));
                assertThat(hold.focusMs()).isEqualTo(600_000);
                assertThat(jdbc.queryForObject("SELECT focus_ms FROM work_records WHERE focus_session_id=?",Long.class,paused))
                        .isEqualTo(600_000);

                var breakClock=new FocusIntegrationTest.MutableClock(breakEnd);
                var breakService=new FocusServiceImpl(store,null,null,null,events,ZoneId.of("Asia/Shanghai"),breakClock);
                OwnerTestContext.use(breakOwner);
                var rest=breakService.get(inBreak);
                rest=breakService.checkpoint(inBreak,new Checkpoint(rest.version(),null,null));
                assertThat(rest.phase()).isEqualTo("RUNNING");
                assertThat(rest.focusMs()).isEqualTo(890_000);
                assertThat(rest.breakMs()).isEqualTo(15_000);
                assertThat(rest.reminderOrdinal()).isEqualTo(1);
                rest=breakService.end(inBreak,new Version(rest.version()));
                assertThat(jdbc.queryForObject("SELECT focus_ms FROM work_records WHERE focus_session_id=?",Long.class,inBreak))
                        .isEqualTo(890_000);
                assertThat(jdbc.queryForObject("SELECT break_ms FROM work_records WHERE focus_session_id=?",Long.class,inBreak))
                        .isEqualTo(15_000);
            });
        } finally {
            OwnerTestContext.use(OwnerTestContext.USER_ID);
            try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){
                statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
            }
        }
    }

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
    @FunctionalInterface
    private interface MigratedFocusAction {
        void run(FocusStore store,JdbcTemplate jdbc) throws Exception;
    }
    private void withMigratedFocusStore(String schema,MigratedFocusAction action) throws Exception {
        try(Connection connection=dataSource.getConnection()){
            String previous=connection.getSchema();connection.setSchema(schema);
            try {
                var scopedDataSource=new SingleConnectionDataSource(connection,true);
                var factoryBean=new SqlSessionFactoryBean();
                factoryBean.setDataSource(scopedDataSource);
                factoryBean.setMapperLocations(new ClassPathResource("mapper/FocusStore.xml"));
                factoryBean.setTypeHandlers(new UuidTypeHandler());
                factoryBean.afterPropertiesSet();
                try(SqlSession session=java.util.Objects.requireNonNull(factoryBean.getObject()).openSession(true)){
                    action.run(session.getMapper(FocusStore.class),new JdbcTemplate(scopedDataSource));
                }
            } finally {connection.setSchema(previous);}
        }
    }
    private <T> T inSchema(String schema,java.util.function.Function<JdbcTemplate,T> action) throws Exception {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()){
            String previous=connection.getSchema();statement.execute("SET search_path TO "+schema);
            try{return action.apply(new JdbcTemplate(new SingleConnectionDataSource(connection,true)));}
            finally{connection.setSchema(previous);}
        }
    }
}
