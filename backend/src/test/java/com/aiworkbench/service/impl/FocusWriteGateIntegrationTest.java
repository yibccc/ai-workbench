package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.mapper.FocusStore;
import com.aiworkbench.service.FocusService;
import com.aiworkbench.support.OwnerTestContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties="workbench.focus.write-enabled=false")
@Transactional
class FocusWriteGateIntegrationTest {
    @Autowired FocusService service;
    @Autowired FocusStore store;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup(){OwnerTestContext.ensureAccounts(jdbc);OwnerTestContext.use(OwnerTestContext.USER_ID);}
    @Test void closedGateBlocksNewWorkButDrainsExistingSession(){
        assertThat(service.capabilities().writeEnabled()).isFalse();
        assertThatThrownBy(()->service.start(new Start(UUID.randomUUID(),"禁止新开",null,null,25,10)))
                .hasMessageContaining("专注写入尚未开放");
        UUID id=UUID.randomUUID();Instant now=Instant.now().minusSeconds(1);
        store.insertSession(id,OwnerTestContext.USER_ID,UUID.randomUUID(),null,null,"已有会话",1_500_000,600_000,
                "Asia/Shanghai",now);
        Session done=service.end(id,new Version(0L));
        assertThat(done.phase()).isEqualTo("ENDED");
        assertThat(done.focusMs()).isPositive();
        assertThat(service.current()).isNull();
    }
}
