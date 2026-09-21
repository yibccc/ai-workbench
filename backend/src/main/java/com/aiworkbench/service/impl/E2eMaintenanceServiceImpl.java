package com.aiworkbench.service.impl;

import com.aiworkbench.e2e.E2eDeterministicAiGateway;
import com.aiworkbench.service.E2eMaintenanceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("e2e & !live-acceptance")
public class E2eMaintenanceServiceImpl implements E2eMaintenanceService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<E2eDeterministicAiGateway> gateway;

    public E2eMaintenanceServiceImpl(JdbcTemplate jdbcTemplate, ObjectProvider<E2eDeterministicAiGateway> gateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.gateway = gateway;
    }

    public void reset() {
        String currentSchema = jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
        if (!"d9_e2e".equals(currentSchema)) {
            throw new IllegalStateException("拒绝清理非 E2E schema: " + currentSchema);
        }
        jdbcTemplate.execute("TRUNCATE TABLE "
                + "d9_e2e.report_sources, d9_e2e.reports, d9_e2e.task_events, "
                + "d9_e2e.work_records, d9_e2e.todo_items, d9_e2e.capture_inputs, "
                + "d9_e2e.projects RESTART IDENTITY CASCADE");
        gateway.ifAvailable(E2eDeterministicAiGateway::reset);
    }
}
