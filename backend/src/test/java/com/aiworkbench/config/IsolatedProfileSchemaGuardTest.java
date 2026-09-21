package com.aiworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsolatedProfileSchemaGuardTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Environment environment = mock(Environment.class);

    @Test
    void acceptsOnlyTheSchemaOwnedByTheSingleActiveProfile() {
        when(environment.matchesProfiles("test")).thenReturn(true);
        when(jdbc.queryForObject("SELECT current_schema()", String.class)).thenReturn("d9_backend_tests");
        assertThatCode(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify()).doesNotThrowAnyException();

        when(jdbc.queryForObject("SELECT current_schema()", String.class)).thenReturn("public");
        assertThatThrownBy(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("非专用 schema");
    }

    @Test
    void rejectsMixedIsolationProfilesBeforeCheckingSchema() {
        when(environment.matchesProfiles("e2e")).thenReturn(true);
        when(environment.matchesProfiles("live-acceptance")).thenReturn(true);
        assertThatThrownBy(() -> new IsolatedProfileSchemaGuard(jdbc, environment).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("只能启用一个");
    }
}
