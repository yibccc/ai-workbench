package com.aiworkbench.support;

import com.aiworkbench.security.WorkbenchAuthentications;
import com.aiworkbench.security.WorkbenchPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit synthetic identity for business integration tests in their isolated schema. */
public final class OwnerTestContext {
    public static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final String PASSWORD = "owner test strong password 123";

    private OwnerTestContext() {}

    public static void ensureAccounts(JdbcTemplate jdbc) {
        requireTestSchema(jdbc);
        String hash = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PASSWORD);
        jdbc.update("""
                INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'owner-test-a',?,'USER')
                ON CONFLICT (id) DO UPDATE SET password_hash=EXCLUDED.password_hash,
                    enabled=TRUE, auth_version=0
                """, USER_ID, hash);
        jdbc.update("""
                INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'owner-test-b',?,'USER')
                ON CONFLICT (id) DO UPDATE SET password_hash=EXCLUDED.password_hash,
                    enabled=TRUE, auth_version=0
                """, OTHER_ID, hash);
        jdbc.update("""
                INSERT INTO user_accounts(id,username,password_hash,role) VALUES (?,'owner-test-admin',?,'ADMIN')
                ON CONFLICT (id) DO UPDATE SET password_hash=EXCLUDED.password_hash,
                    enabled=TRUE, auth_version=0
                """, ADMIN_ID, hash);
    }

    public static void removeAccounts(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM user_accounts WHERE id IN (?, ?, ?)", USER_ID, OTHER_ID, ADMIN_ID);
    }

    public static void removeBusinessData(JdbcTemplate jdbc) {
        requireTestSchema(jdbc);
        // This helper is only used against isolated d9 test schemas.
        jdbc.update("DELETE FROM report_sources WHERE report_id IN (SELECT id FROM reports WHERE user_id IN (?,?,?))", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("UPDATE reports SET previous_report_id=NULL WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM reports WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM task_events WHERE todo_id IN (SELECT id FROM todo_items WHERE user_id IN (?,?,?))", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM work_records WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM capture_generated_items WHERE input_id IN (SELECT id FROM capture_inputs WHERE user_id IN (?,?,?))", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM todo_items WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM capture_inputs WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        jdbc.update("DELETE FROM projects WHERE user_id IN (?,?,?)", USER_ID, OTHER_ID, ADMIN_ID);
        removeAccounts(jdbc);
    }

    private static void requireTestSchema(JdbcTemplate jdbc) {
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        if (!"d9_backend_tests".equals(schema)
                && (schema == null || !schema.matches("d9_[a-z0-9_]+_tests_[0-9]{8}"))) {
            throw new IllegalStateException("Owner fixtures require an approved d9 test schema");
        }
    }

    public static void use(UUID userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(WorkbenchAuthentications.authentication(
                new WorkbenchPrincipal(userId, ADMIN_ID.equals(userId) ? "ADMIN" : "USER", 0)));
        SecurityContextHolder.setContext(context);
    }

    public static Cookie login(MockMvc mvc) throws Exception {
        return login(mvc, "owner-test-a");
    }

    public static Cookie login(MockMvc mvc, String username) throws Exception {
        Cookie session = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("WORKBENCH_SESSION");
        if (session == null) throw new IllegalStateException("测试登录未返回会话 Cookie");
        return session;
    }

    public static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, Cookie session) {
        return authenticated(request, session, USER_ID, "USER", 0);
    }

    public static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, Cookie session,
            UUID userId, String role, long authVersion) {
        return request.cookie(session).with(authentication(WorkbenchAuthentications.authentication(
                new WorkbenchPrincipal(userId, role, authVersion))));
    }

    public static <T> Callable<T> as(UUID userId, Callable<T> work) {
        return () -> {
            try {
                use(userId);
                return work.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
