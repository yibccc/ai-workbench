package com.aiworkbench.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Prevents test and acceptance profiles from ever writing through the public schema. */
@Component
@Profile({"test", "e2e", "live-acceptance"})
public class IsolatedProfileSchemaGuard {
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public IsolatedProfileSchemaGuard(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        boolean test = environment.matchesProfiles("test");
        boolean e2e = environment.matchesProfiles("e2e");
        boolean live = environment.matchesProfiles("live-acceptance");
        int activeIsolationProfiles = (test ? 1 : 0) + (e2e ? 1 : 0) + (live ? 1 : 0);
        if (activeIsolationProfiles != 1) {
            throw new IllegalStateException("测试与验收 Profile 必须且只能启用一个");
        }
        String expected = test ? "d9_backend_tests" : e2e ? "d9_e2e" : "d9_live_acceptance";
        String actual = jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("隔离 Profile 拒绝使用非专用 schema: " + actual);
        }
    }
}
