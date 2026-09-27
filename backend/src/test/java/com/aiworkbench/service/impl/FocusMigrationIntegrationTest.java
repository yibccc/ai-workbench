package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.service.FocusService;
import com.aiworkbench.service.ProjectService;
import com.aiworkbench.dto.project.CreateProjectRequest;
import com.aiworkbench.support.OwnerTestContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class FocusMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FocusStore store;
    @Autowired FocusService service;
    @Autowired ProjectService projects;
    @BeforeEach void setup(){OwnerTestContext.ensureAccounts(jdbc);OwnerTestContext.use(OwnerTestContext.USER_ID);}
    @Test void uniqueOccurrenceIncludesSoftDeletedRows(){
        int weekday=LocalDate.now(ZoneId.of("Asia/Shanghai")).getDayOfWeek().getValue();
        Routine r=service.createRoutine(new SaveRoutine("日报复盘",null,List.of(weekday),25,null,null));
        var first=service.fillToday().created().get(0);
        jdbc.update("UPDATE todo_items SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",first.id());
        assertThat(store.insertOccurrence(UUID.randomUUID(),OwnerTestContext.USER_ID,null,"替身",r.id(),
                first.occurrenceDate(),25)).isZero();
    }
    @Test void crossOwnerRoutineProjectForeignKeyIsRejected(){
        var project=projects.create(new CreateProjectRequest("甲账号项目"+UUID.randomUUID()));
        assertThatThrownBy(()->store.insertRoutine(OwnerTestContext.OTHER_ID,UUID.randomUUID(),"越权",project.id(),"1",25))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void zeroLengthIntervalCannotBePersisted(){
        Session s=service.start(new Start(UUID.randomUUID(),"区间",null,null,25,10));
        Instant at=s.startedAt();
        assertThat(store.insertInterval(OwnerTestContext.USER_ID,s.id(),"FOCUS",at,at,true)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM focus_intervals WHERE session_id=?",Integer.class,s.id())).isZero();
    }
    @Test void sameSessionDayFocusRecordUniqueIndexRejectsDuplicate(){
        Session s=service.start(new Start(UUID.randomUUID(),"分片",null,null,25,10));
        Instant start=s.startedAt(),end=start.plusSeconds(1);LocalDate date=start.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        assertThat(store.insertFocusRecord(UUID.randomUUID(),OwnerTestContext.USER_ID,null,null,s.id(),"投入",date,1000,0,start,end)).isEqualTo(1);
        assertThat(store.insertFocusRecord(UUID.randomUUID(),OwnerTestContext.USER_ID,null,null,s.id(),"重复",date,1000,0,start,end)).isZero();
    }
}
