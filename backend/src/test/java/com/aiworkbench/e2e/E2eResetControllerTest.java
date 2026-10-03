package com.aiworkbench.e2e;

import com.aiworkbench.controller.E2eResetController;
import com.aiworkbench.service.impl.E2eMaintenanceServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class E2eResetControllerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<E2eDeterministicAiGateway> gateway = mock(ObjectProvider.class);
    private final E2eResetController controller = new E2eResetController(new E2eMaintenanceServiceImpl(jdbcTemplate, gateway));

    @Test
    void refusesToResetAnySchemaOtherThanTheDedicatedE2eSchema() {
        when(jdbcTemplate.queryForObject("SELECT current_schema()", String.class)).thenReturn("public");

        assertThatThrownBy(controller::reset)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非 E2E schema");

        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.startsWith("TRUNCATE"));
    }

    @Test
    void truncatesOnlySchemaQualifiedE2eTables() {
        when(jdbcTemplate.queryForObject("SELECT current_schema()", String.class)).thenReturn("d9_e2e");

        controller.reset();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        org.assertj.core.api.Assertions.assertThat(sql.getValue())
                .startsWith("TRUNCATE TABLE d9_e2e.report_sources")
                .contains("d9_e2e.community_post_revisions", "d9_e2e.community_posts", "d9_e2e.community_public_profiles")
                .doesNotContain(" TABLE report_sources");
    }
}
