package com.aiworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsolatedProfileSchemaGuardTest {
    @Test
    void acceptsAnExplicitDatedTestSchemaOnlyWhenTheConnectionMatches() {
        var environment = new MockEnvironment()
                .withProperty("workbench.test.schema", "d9_owner_tests_20260925");
        environment.setActiveProfiles("test");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT current_schema()", String.class))
                .thenReturn("d9_owner_tests_20260925");

        assertThatCode(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPublicEvenWithAnExplicitOverride() {
        var environment = new MockEnvironment().withProperty("workbench.test.schema", "public");
        environment.setActiveProfiles("test");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("非专用 schema");
    }

    @Test
    void otherIsolationProfilesCannotUseTheTestOverride() {
        var environment = new MockEnvironment()
                .withProperty("workbench.test.schema", "d9_owner_tests_20260925");
        environment.setActiveProfiles("e2e");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT current_schema()", String.class))
                .thenReturn("d9_owner_tests_20260925");

        assertThatThrownBy(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("非专用 schema");
    }
}
