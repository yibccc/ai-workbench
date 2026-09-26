package com.aiworkbench.security;

import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.service.AccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {
    @LocalServerPort int port;
    @MockitoSpyBean AccountService accounts;
    @MockitoSpyBean LoginSessionService logins;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired ObjectMapper mapper;
    @Autowired AdjustableClock clock;
    @MockitoSpyBean FindByIndexNameSessionRepository<? extends Session> sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean SessionActivity activity;

    @Test
    void loginRequiresCsrfAndProtectsAdministrationAndCurrentSession() throws Exception {
        clock.reset();
        String username = "auth-test-" + UUID.randomUUID();
        AccountRow admin = accounts.create(username, "a strong test password 123", "ADMIN");
        try {
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
            assertThat(send(client, "GET", "/api/projects", null, null).statusCode()).isEqualTo(401);
            assertThat(send(client, "POST", "/api/auth/login",
                    json("username", username, "password", "a strong test password 123"), null).statusCode())
                    .isEqualTo(403);

            JsonNode csrf = mapper.readTree(send(client, "GET", "/api/auth/csrf", null, null).body());
            String token = csrf.get("token").asText();
            HttpResponse<String> loggedIn = send(client, "POST", "/api/auth/login",
                    json("username", username, "password", "a strong test password 123"), token);
            assertThat(loggedIn.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(loggedIn.body()).get("id").asText()).isEqualTo(admin.id().toString());
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            assertThat(send(client, "POST", "/api/auth/activity", "{}", token).statusCode()).isEqualTo(200);
            assertThat(send(client, "POST", "/api/auth/logout", "{}", token).statusCode()).isEqualTo(200);
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", admin.id());
        }
    }

    @Test
    void userCannotAdministerAndResetRevokesBothIndependentSessions() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-admin-" + UUID.randomUUID(), "strong admin password 123", "ADMIN");
        AccountRow user = accounts.create("auth-test-user-" + UUID.randomUUID(), "strong user password 123", "USER");
        try {
            HttpClient adminClient = newClient();
            HttpClient first = newClient();
            HttpClient second = newClient();
            String adminCsrf = csrf(adminClient);
            String firstCsrf = csrf(first);
            String secondCsrf = csrf(second);
            assertThat(login(adminClient, admin, "strong admin password 123", adminCsrf).statusCode()).isEqualTo(200);
            assertThat(login(first, user, "strong user password 123", firstCsrf).statusCode()).isEqualTo(200);
            assertThat(login(second, user, "strong user password 123", secondCsrf).statusCode()).isEqualTo(200);
            assertThat(sessions.findByPrincipalName(user.id().toString())).hasSize(2);
            HttpResponse<String> forbidden = send(first, "GET", "/api/admin/users", null, null);
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertThat(mapper.readTree(forbidden.body()).get("detail").asText()).isNotBlank();
            assertThat(send(first, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            assertThat(send(second, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);

            HttpResponse<String> reset = send(adminClient, "POST",
                    "/api/admin/users/" + user.id() + "/reset-password",
                    mapper.writeValueAsString(java.util.Map.of("password", "new strong password 123")), adminCsrf);
            assertThat(reset.statusCode()).isEqualTo(200);
            assertThat(sessions.findByPrincipalName(user.id().toString())).isEmpty();
            assertThat(send(first, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            assertThat(send(second, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpClient oldPassword = newClient();
            assertThat(login(oldPassword, user, "strong user password 123", csrf(oldPassword)).statusCode())
                    .isEqualTo(401);
            HttpClient fresh = newClient();
            assertThat(login(fresh, user, "new strong password 123", csrf(fresh)).statusCode()).isEqualTo(200);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", user.id(), admin.id());
        }
    }

    @Test
    void ordinaryUserCannotCallE2eResetWithValidSessionAndCsrf() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-e2e-reset-" + UUID.randomUUID(),
                "strong reset password 123", "USER");
        try {
            HttpClient client = newClient();
            String token = csrf(client);
            assertThat(login(client, user, "strong reset password 123", token).statusCode()).isEqualTo(200);

            HttpResponse<String> rejected = send(client, "POST", "/api/e2e/reset", "{}", token);
            assertThat(rejected.statusCode()).isEqualTo(403);
            assertThat(mapper.readTree(rejected.body()).get("status").asInt()).isEqualTo(403);
            assertThat(mapper.readTree(rejected.body()).get("detail").asText()).isNotBlank();
            assertThat(rejected.body()).doesNotContain("RESET");
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void missingActivityMarkerFailsClosedAndCannotBeRecreatedByActivitySignal() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-marker-" + UUID.randomUUID(),
                "strong marker password 123", "USER");
        try {
            HttpClient client = newClient();
            String token = csrf(client);
            assertThat(login(client, user, "strong marker password 123", token).statusCode()).isEqualTo(200);
            String sessionId = sessions.findByPrincipalName(user.id().toString()).keySet().iterator().next();
            // Simulate a missing Redis activity marker while the framework Session still exists.
            activity.remove(sessionId);

            HttpResponse<String> rejected = send(client, "POST", "/api/auth/activity", "{}", token);
            assertThat(rejected.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(rejected.body()).get("detail").asText()).contains("会话");
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void redisActivityReadFailureDeniesRealProtectedHttpRequest() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-redis-" + UUID.randomUUID(),
                "strong redis password 123", "USER");
        try {
            HttpClient client = newClient();
            assertThat(login(client, user, "strong redis password 123", csrf(client)).statusCode()).isEqualTo(200);
            String sessionId = sessions.findByPrincipalName(user.id().toString()).keySet().iterator().next();
            doThrow(new RedisConnectionFailureException("simulated activity Redis failure"))
                    .when(activity).isLive(eq(sessionId));

            HttpResponse<String> rejected = send(client, "GET", "/api/auth/me", null, null);
            assertThat(rejected.statusCode()).isEqualTo(503);
            assertThat(mapper.readTree(rejected.body()).get("detail").asText()).isEqualTo("认证服务暂不可用");
            assertThat(rejected.body()).doesNotContain(user.username());
            assertThat(rejected.body()).doesNotContain(user.id().toString());
        } finally {
            reset(activity);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void springSessionReadFailureDoesNotExposeProtectedHttpContent() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-session-redis-" + UUID.randomUUID(),
                "strong session password 123", "USER");
        try {
            HttpClient client = newClient();
            assertThat(login(client, user, "strong session password 123", csrf(client)).statusCode()).isEqualTo(200);
            String sessionId = sessions.findByPrincipalName(user.id().toString()).keySet().iterator().next();
            doThrow(new RedisConnectionFailureException("simulated Spring Session Redis read failure"))
                    .when(sessions).findById(eq(sessionId));

            HttpResponse<String> rejected = send(client, "GET", "/api/auth/me", null, null);
            assertThat(rejected.statusCode()).isGreaterThanOrEqualTo(500);
            assertThat(rejected.body()).doesNotContain(user.username(), user.id().toString());
            assertThat(rejected.body()).doesNotContain("RedisConnectionFailureException", "simulated Spring Session");
        } finally {
            reset(sessions);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void emptyTableConcurrentBootstrapCreatesExactlyOneAdminAndLaterConfigCannotResetIt() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo(environment.getProperty("workbench.test.schema", "d9_backend_tests"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts", Long.class)).isZero();
        String firstName = "auth-test-bootstrap-a-" + UUID.randomUUID();
        String secondName = "auth-test-bootstrap-b-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<AccountRow> first = executor.submit(() -> {
                start.await();
                return accounts.bootstrap(firstName, "first strong password 123");
            });
            Future<AccountRow> second = executor.submit(() -> {
                start.await();
                return accounts.bootstrap(secondName, "second strong password 123");
            });
            start.countDown();
            AccountRow a = first.get(10, TimeUnit.SECONDS);
            AccountRow b = second.get(10, TimeUnit.SECONDS);
            assertThat((a == null ? 0 : 1) + (b == null ? 0 : 1)).isEqualTo(1);
            AccountRow created = a == null ? b : a;
            assertThat(created.role()).isEqualTo("ADMIN");
            assertThat(created.enabled()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts", Long.class)).isEqualTo(1L);

            String originalHash = created.passwordHash();
            assertThat(accounts.bootstrap("ignored-extra-" + UUID.randomUUID(), "different strong password 123"))
                    .isNull();
            assertThat(accounts.bootstrap("", "bad")).isNull();
            AccountRow unchanged = accounts.require(created.id());
            assertThat(unchanged.passwordHash()).isEqualTo(originalHash);
            assertThat(unchanged.authVersion()).isEqualTo(0L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM user_accounts WHERE username IN (?,?)", firstName, secondName);
        }
    }

    @Test
    void loginRacingAdminResetCannotUseOldVersion() throws Exception {
        assertRacingLoginCannotUseOldVersion(false);
    }

    @Test
    void loginRacingAdminDisableCannotUseOldVersion() throws Exception {
        assertRacingLoginCannotUseOldVersion(true);
    }

    @Test
    void adminResetWaitsWhileLoginHoldsAccountVersionLock() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-lock-admin-" + UUID.randomUUID(),
                "strong admin password 123", "ADMIN");
        AccountRow user = accounts.create("auth-test-lock-user-" + UUID.randomUUID(),
                "strong user password 123", "USER");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch finishLogin = new CountDownLatch(1);
        try {
            HttpClient adminClient = newClient();
            String adminToken = csrf(adminClient);
            assertThat(login(adminClient, admin, "strong admin password 123", adminToken).statusCode()).isEqualTo(200);
            HttpClient userClient = newClient();
            String userToken = csrf(userClient);
            doAnswer(invocation -> {
                AccountRow old = (AccountRow) invocation.callRealMethod();
                captured.countDown();
                if (!finishLogin.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("login lock gate timed out");
                }
                return old;
            }).when(accounts).findByUsername(eq(user.username()));

            Future<HttpResponse<String>> loginRequest = executor.submit(() ->
                    login(userClient, user, "strong user password 123", userToken));
            assertThat(captured.await(10, TimeUnit.SECONDS)).isTrue();
            Future<HttpResponse<String>> resetRequest = executor.submit(() ->
                    send(adminClient, "POST", "/api/admin/users/" + user.id() + "/reset-password",
                            "{\"password\":\"new strong password 123\"}", adminToken));
            boolean waitingForLock = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !waitingForLock) {
                waitingForLock = jdbc.queryForObject("""
                        SELECT count(*) FROM pg_locks
                        WHERE locktype='advisory' AND objid=1821734041::oid AND NOT granted
                        """, Long.class) > 0;
                if (!waitingForLock) {
                    Thread.sleep(20);
                }
            }
            assertThat(waitingForLock).isTrue();
            assertThat(resetRequest.isDone()).isFalse();
            finishLogin.countDown();
            assertThat(loginRequest.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(resetRequest.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(send(userClient, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            finishLogin.countDown();
            reset(accounts);
            executor.shutdownNow();
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", user.id(), admin.id());
        }
    }

    private void assertRacingLoginCannotUseOldVersion(boolean disable) throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-race-admin-" + UUID.randomUUID(),
                "strong admin password 123", "ADMIN");
        AccountRow user = accounts.create("auth-test-race-user-" + UUID.randomUUID(),
                "strong user password 123", "USER");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch enteredLogin = new CountDownLatch(1);
        CountDownLatch finishLogin = new CountDownLatch(1);
        try {
            HttpClient adminClient = newClient();
            String adminToken = csrf(adminClient);
            assertThat(login(adminClient, admin, "strong admin password 123", adminToken).statusCode()).isEqualTo(200);
            HttpClient userClient = newClient();
            String userToken = csrf(userClient);

            doAnswer(invocation -> {
                enteredLogin.countDown();
                if (!finishLogin.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("login race gate timed out");
                }
                return invocation.callRealMethod();
            }).when(logins).login(eq(user.username()), eq("strong user password 123"), any(), any());
            Future<HttpResponse<String>> raced = executor.submit(() ->
                    login(userClient, user, "strong user password 123", userToken));
            assertThat(enteredLogin.await(10, TimeUnit.SECONDS)).isTrue();

            HttpResponse<String> changed = disable
                    ? send(adminClient, "PATCH", "/api/admin/users/" + user.id() + "/enabled",
                            "{\"enabled\":false}", adminToken)
                    : send(adminClient, "POST", "/api/admin/users/" + user.id() + "/reset-password",
                            "{\"password\":\"new strong password 123\"}", adminToken);
            assertThat(changed.statusCode()).isEqualTo(200);
            finishLogin.countDown();
            HttpResponse<String> loginResult = raced.get(10, TimeUnit.SECONDS);
            assertThat(loginResult.statusCode()).isEqualTo(401);
            HttpResponse<String> blocked = send(userClient, "GET", "/api/projects", null, null);
            assertThat(blocked.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(blocked.body()).get("status").asInt()).isEqualTo(401);
            assertThat(blocked.body()).doesNotContain(user.id().toString(), user.username());
        } finally {
            finishLogin.countDown();
            reset(logins);
            executor.shutdownNow();
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", user.id(), admin.id());
        }
    }

    @Test
    void changingOwnPasswordKeepsOnlyCurrentSession() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-self-" + UUID.randomUUID(), "old strong password 123", "USER");
        try {
            HttpClient first = newClient();
            HttpClient second = newClient();
            String firstCsrf = csrf(first);
            String secondCsrf = csrf(second);
            assertThat(login(first, user, "old strong password 123", firstCsrf).statusCode()).isEqualTo(200);
            assertThat(login(second, user, "old strong password 123", secondCsrf).statusCode()).isEqualTo(200);
            assertThat(sessions.findByPrincipalName(user.id().toString())).hasSize(2);
            HttpResponse<String> changed = send(first, "POST", "/api/auth/password",
                    mapper.writeValueAsString(java.util.Map.of(
                            "currentPassword", "old strong password 123", "newPassword", "new strong password 123")),
                    firstCsrf);
            assertThat(changed.statusCode()).isEqualTo(200);
            assertThat(sessions.findByPrincipalName(user.id().toString())).hasSize(1);
            assertThat(send(first, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            assertThat(send(second, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void automaticRequestsDoNotExtendIdleDeadlineAndExpiredActivityCannotRevive() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-idle-" + UUID.randomUUID(), "strong idle password 123", "USER");
        try {
            HttpClient passive = newClient();
            String passiveCsrf = csrf(passive);
            assertThat(login(passive, user, "strong idle password 123", passiveCsrf).statusCode()).isEqualTo(200);
            long firstLogin = clock.millis();
            clock.set(firstLogin + SessionActivity.IDLE_MILLIS - 1);
            assertThat(send(passive, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            clock.set(firstLogin + SessionActivity.IDLE_MILLIS);
            assertThat(send(passive, "POST", "/api/auth/activity", "{}", passiveCsrf).statusCode()).isEqualTo(401);

            HttpClient active = newClient();
            String activeCsrf = csrf(active);
            assertThat(login(active, user, "strong idle password 123", activeCsrf).statusCode()).isEqualTo(200);
            long secondLogin = clock.millis();
            clock.set(secondLogin + SessionActivity.IDLE_MILLIS - 1);
            assertThat(send(active, "POST", "/api/auth/activity", "{}", activeCsrf).statusCode()).isEqualTo(200);
            clock.set(secondLogin + SessionActivity.IDLE_MILLIS);
            assertThat(send(active, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            clock.set(secondLogin + 2 * SessionActivity.IDLE_MILLIS - 1);
            assertThat(send(active, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void concurrentDemotionsCannotRemoveTheLastEnabledAdministrator() throws Exception {
        AccountRow first = accounts.create("auth-test-last-a-" + UUID.randomUUID(), "strong admin password 123", "ADMIN");
        AccountRow second = accounts.create("auth-test-last-b-" + UUID.randomUUID(), "strong admin password 123", "ADMIN");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> one = executor.submit(() -> {
                start.await();
                try { accounts.changeEnabled(first.id(), false); return true; }
                catch (ResponseStatusException exception) { return false; }
            });
            Future<Boolean> two = executor.submit(() -> {
                start.await();
                try { accounts.changeEnabled(second.id(), false); return true; }
                catch (ResponseStatusException exception) { return false; }
            });
            start.countDown();
            assertThat((one.get() ? 1 : 0) + (two.get() ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE id IN (?,?) AND enabled AND role='ADMIN'",
                    Long.class, first.id(), second.id())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", first.id(), second.id());
        }
    }

    @Test
    void passwordBoundaryCountsUnicodeCodePointsWithoutByteTruncation() {
        assertThatThrownBy(() -> accounts.create("auth-test-short-" + UUID.randomUUID(),
                "🐬".repeat(7), "USER")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> accounts.create("auth-test-long-" + UUID.randomUUID(),
                "🐬".repeat(65), "USER")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> accounts.create("auth-test-weak-" + UUID.randomUUID(),
                "password123", "USER")).isInstanceOf(ResponseStatusException.class);

        AccountRow eight = accounts.create("auth-test-eight-" + UUID.randomUUID(), "🐬".repeat(8), "USER");
        AccountRow sixtyFour = accounts.create("auth-test-sixty-four-" + UUID.randomUUID(),
                "🐬".repeat(64), "USER");
        try {
            assertThat(passwords.matches("🐬".repeat(8), eight.passwordHash())).isTrue();
            assertThat(passwords.matches("🐬".repeat(64), sixtyFour.passwordHash())).isTrue();
            assertThat(passwords.matches("🐬".repeat(63) + "🐟", sixtyFour.passwordHash())).isFalse();
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", eight.id(), sixtyFour.id());
        }
    }

    @Test
    void loginRateLimitHasFiniteWindowAndSuccessClearsOnlyThatAccount() throws Exception {
        clock.reset();
        AccountRow first = accounts.create("auth-test-limit-a-" + UUID.randomUUID(),
                "strong first password 123", "USER");
        AccountRow second = accounts.create("auth-test-limit-b-" + UUID.randomUUID(),
                "strong second password 123", "USER");
        String firstKey = limiterKey(first.username());
        String secondKey = limiterKey(second.username());
        try {
            HttpClient firstClient = newClient();
            HttpClient secondClient = newClient();
            String firstToken = csrf(firstClient);
            String secondToken = csrf(secondClient);
            for (int attempt = 0; attempt < 5; attempt++) {
                String submittedName = attempt == 0 ? first.username().toUpperCase(Locale.ROOT) : first.username();
                HttpResponse<String> failed = send(firstClient, "POST", "/api/auth/login",
                        json("username", submittedName, "password", "wrong password 123"), firstToken);
                assertThat(failed.statusCode()).isEqualTo(401);
            }
            assertThat(redis.opsForValue().get(firstKey + ":count")).isEqualTo("5");
            assertThat(redis.getExpire(firstKey + ":count", TimeUnit.SECONDS)).isBetween(850L, 900L);
            assertThat(redis.getExpire(firstKey + ":wait", TimeUnit.SECONDS)).isBetween(55L, 60L);
            assertThat(login(firstClient, first, "strong first password 123", firstToken).statusCode())
                    .isEqualTo(429);

            for (int attempt = 0; attempt < 2; attempt++) {
                assertThat(login(secondClient, second, "wrong password 123", secondToken).statusCode())
                        .isEqualTo(401);
            }
            assertThat(redis.opsForValue().get(secondKey + ":count")).isEqualTo("2");
            // Model the end of the 60-second wait without sleeping or affecting unrelated Redis keys.
            redis.delete(firstKey + ":wait");
            assertThat(login(firstClient, first, "strong first password 123", firstToken).statusCode())
                    .isEqualTo(200);
            assertThat(redis.hasKey(firstKey + ":count")).isFalse();
            assertThat(redis.hasKey(firstKey + ":wait")).isFalse();
            assertThat(redis.opsForValue().get(secondKey + ":count")).isEqualTo("2");
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThat(login(secondClient, second, "wrong password 123", secondToken).statusCode())
                        .isEqualTo(401);
            }
            assertThat(login(secondClient, second, "strong second password 123", secondToken).statusCode())
                    .isEqualTo(429);
        } finally {
            redis.delete(List.of(firstKey + ":count", firstKey + ":wait",
                    secondKey + ":count", secondKey + ":wait"));
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", first.id(), second.id());
        }
    }

    @Test
    void administratorApiRejectsCaseInsensitiveDuplicateUsername() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-unique-admin-" + UUID.randomUUID(),
                "strong admin password 123", "ADMIN");
        String username = "Alice-" + UUID.randomUUID();
        try {
            HttpClient client = newClient();
            String token = csrf(client);
            assertThat(login(client, admin, "strong admin password 123", token).statusCode()).isEqualTo(200);
            HttpResponse<String> created = send(client, "POST", "/api/admin/users",
                    mapper.writeValueAsString(java.util.Map.of("username", username,
                            "password", "strong new password 123", "role", "USER")), token);
            assertThat(created.statusCode()).isEqualTo(201);
            HttpResponse<String> conflict = send(client, "POST", "/api/admin/users",
                    mapper.writeValueAsString(java.util.Map.of("username", username.toUpperCase(Locale.ROOT),
                            "password", "strong new password 123", "role", "USER")), token);
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(mapper.readTree(conflict.body()).get("detail").asText()).isNotBlank();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE lower(username)=lower(?)",
                    Long.class, username)).isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE lower(username)=lower(?)", username);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", admin.id());
        }
    }

    @Test
    void roleChangeAndSelfDemotionRevokeAllOldSessionsWhileLastAdminIsProtected() throws Exception {
        clock.reset();
        AccountRow first = accounts.create("auth-test-role-a-" + UUID.randomUUID(),
                "strong first password 123", "ADMIN");
        AccountRow second = accounts.create("auth-test-role-b-" + UUID.randomUUID(),
                "strong second password 123", "ADMIN");
        AccountRow third = accounts.create("auth-test-role-c-" + UUID.randomUUID(),
                "strong third password 123", "ADMIN");
        try {
            HttpClient firstOne = newClient();
            HttpClient firstTwo = newClient();
            HttpClient secondOne = newClient();
            HttpClient secondTwo = newClient();
            HttpClient thirdOne = newClient();
            String firstOneToken = csrf(firstOne);
            String firstTwoToken = csrf(firstTwo);
            String secondOneToken = csrf(secondOne);
            String secondTwoToken = csrf(secondTwo);
            String thirdToken = csrf(thirdOne);
            assertThat(login(firstOne, first, "strong first password 123", firstOneToken).statusCode()).isEqualTo(200);
            assertThat(login(firstTwo, first, "strong first password 123", firstTwoToken).statusCode()).isEqualTo(200);
            assertThat(login(secondOne, second, "strong second password 123", secondOneToken).statusCode()).isEqualTo(200);
            assertThat(login(secondTwo, second, "strong second password 123", secondTwoToken).statusCode()).isEqualTo(200);
            assertThat(login(thirdOne, third, "strong third password 123", thirdToken).statusCode()).isEqualTo(200);

            assertThat(send(secondOne, "PATCH", "/api/admin/users/" + first.id() + "/role",
                    "{\"role\":\"USER\"}", secondOneToken).statusCode()).isEqualTo(200);
            assertThat(send(firstOne, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            assertThat(send(firstTwo, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpClient changedFirst = newClient();
            assertThat(login(changedFirst, first, "strong first password 123", csrf(changedFirst)).statusCode())
                    .isEqualTo(200);
            assertThat(send(changedFirst, "GET", "/api/admin/users", null, null).statusCode()).isEqualTo(403);

            assertThat(send(secondOne, "PATCH", "/api/admin/users/" + second.id() + "/role",
                    "{\"role\":\"USER\"}", secondOneToken).statusCode()).isEqualTo(200);
            assertThat(send(secondOne, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            assertThat(send(secondTwo, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpResponse<String> lastAdmin = send(thirdOne, "PATCH",
                    "/api/admin/users/" + third.id() + "/role", "{\"role\":\"USER\"}", thirdToken);
            assertThat(lastAdmin.statusCode()).isEqualTo(409);
            assertThat(mapper.readTree(lastAdmin.body()).get("detail").asText()).isNotBlank();
            assertThat(send(thirdOne, "GET", "/api/admin/users", null, null).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE enabled AND role='ADMIN'",
                    Long.class)).isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?,?)", first.id(), second.id(), third.id());
        }
    }

    @Test
    void adminCreateAndResetShareUnicodeAndWeakPasswordRulesThroughHttp() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-password-admin-" + UUID.randomUUID(),
                "strong admin password 123", "ADMIN");
        String eightName = "auth-test-create-eight-" + UUID.randomUUID();
        String sixtyFourName = "auth-test-create-sixty-four-" + UUID.randomUUID();
        String eight = "🐬".repeat(8);
        String sixtyFour = "🐬".repeat(64);
        String[] invalid = {"🐬".repeat(7), "🐬".repeat(65), "password123"};
        try {
            HttpClient adminClient = newClient();
            String adminToken = csrf(adminClient);
            assertThat(login(adminClient, admin, "strong admin password 123", adminToken).statusCode())
                    .isEqualTo(200);
            for (String bad : invalid) {
                HttpResponse<String> rejected = send(adminClient, "POST", "/api/admin/users",
                        mapper.writeValueAsString(java.util.Map.of("username", eightName,
                                "password", bad, "role", "USER")), adminToken);
                assertThat(rejected.statusCode()).isEqualTo(400);
                assertThat(mapper.readTree(rejected.body()).get("detail").asText()).isNotBlank();
            }
            assertThat(accounts.findByUsername(eightName)).isNull();

            assertThat(send(adminClient, "POST", "/api/admin/users",
                    mapper.writeValueAsString(java.util.Map.of("username", eightName,
                            "password", eight, "role", "USER")), adminToken).statusCode()).isEqualTo(201);
            assertThat(send(adminClient, "POST", "/api/admin/users",
                    mapper.writeValueAsString(java.util.Map.of("username", sixtyFourName,
                            "password", sixtyFour, "role", "USER")), adminToken).statusCode()).isEqualTo(201);
            AccountRow target = accounts.findByUsername(eightName);
            HttpClient targetClient = newClient();
            assertThat(login(targetClient, target, eight, csrf(targetClient)).statusCode()).isEqualTo(200);
            HttpClient longPasswordClient = newClient();
            assertThat(login(longPasswordClient, accounts.findByUsername(sixtyFourName), sixtyFour,
                    csrf(longPasswordClient)).statusCode()).isEqualTo(200);

            for (String bad : invalid) {
                HttpResponse<String> rejected = send(adminClient, "POST",
                        "/api/admin/users/" + target.id() + "/reset-password",
                        mapper.writeValueAsString(java.util.Map.of("password", bad)), adminToken);
                assertThat(rejected.statusCode()).isEqualTo(400);
                assertThat(mapper.readTree(rejected.body()).get("detail").asText()).isNotBlank();
                assertThat(send(targetClient, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            }
            assertThat(accounts.require(target.id()).authVersion()).isEqualTo(0L);
            assertThat(send(adminClient, "POST", "/api/admin/users/" + target.id() + "/reset-password",
                    mapper.writeValueAsString(java.util.Map.of("password", sixtyFour)), adminToken).statusCode())
                    .isEqualTo(200);
            assertThat(send(targetClient, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpClient afterReset = newClient();
            String afterResetToken = csrf(afterReset);
            assertThat(login(afterReset, target, eight, afterResetToken).statusCode()).isEqualTo(401);
            assertThat(login(afterReset, target, sixtyFour, afterResetToken).statusCode()).isEqualTo(200);

            assertThat(send(adminClient, "POST", "/api/admin/users/" + target.id() + "/reset-password",
                    mapper.writeValueAsString(java.util.Map.of("password", eight)), adminToken).statusCode())
                    .isEqualTo(200);
            assertThat(send(afterReset, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpClient afterSecondReset = newClient();
            assertThat(login(afterSecondReset, target, eight, csrf(afterSecondReset)).statusCode())
                    .isEqualTo(200);
        } finally {
            redis.delete(List.of(limiterKey(eightName) + ":count", limiterKey(eightName) + ":wait"));
            jdbc.update("DELETE FROM user_accounts WHERE username IN (?,?)", eightName, sixtyFourName);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", admin.id());
        }
    }

    @Test
    void selfChangeRejectsInvalidPasswordsWithoutChangingSessionAndReplacesOldPasswordOnSuccess() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("auth-test-self-rules-" + UUID.randomUUID(),
                "old strong password 123", "USER");
        String eight = "🐬".repeat(8);
        String sixtyFour = "🐬".repeat(64);
        try {
            HttpClient client = newClient();
            String token = csrf(client);
            assertThat(login(client, user, "old strong password 123", token).statusCode()).isEqualTo(200);
            HttpResponse<String> wrongCurrent = send(client, "POST", "/api/auth/password",
                    json("currentPassword", "wrong current password", "newPassword", eight), token);
            assertThat(wrongCurrent.statusCode()).isEqualTo(400);
            assertThat(accounts.require(user.id()).authVersion()).isEqualTo(0L);
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);

            for (String bad : new String[]{"🐬".repeat(7), "🐬".repeat(65), "password123"}) {
                HttpResponse<String> rejected = send(client, "POST", "/api/auth/password",
                        json("currentPassword", "old strong password 123", "newPassword", bad), token);
                assertThat(rejected.statusCode()).isEqualTo(400);
                assertThat(mapper.readTree(rejected.body()).get("detail").asText()).isNotBlank();
            }
            assertThat(accounts.require(user.id()).authVersion()).isEqualTo(0L);
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            HttpClient oldPassword = newClient();
            assertThat(login(oldPassword, user, "old strong password 123", csrf(oldPassword)).statusCode())
                    .isEqualTo(200);

            assertThat(send(client, "POST", "/api/auth/password",
                    json("currentPassword", "old strong password 123", "newPassword", eight), token).statusCode())
                    .isEqualTo(200);
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            assertThat(send(oldPassword, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            HttpClient checkOld = newClient();
            assertThat(login(checkOld, user, "old strong password 123", csrf(checkOld)).statusCode())
                    .isEqualTo(401);

            assertThat(send(client, "POST", "/api/auth/password",
                    json("currentPassword", eight, "newPassword", sixtyFour), token).statusCode())
                    .isEqualTo(200);
            assertThat(send(client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            HttpClient fresh = newClient();
            String freshToken = csrf(fresh);
            assertThat(login(fresh, user, eight, freshToken).statusCode()).isEqualTo(401);
            assertThat(login(fresh, user, sixtyFour, freshToken).statusCode()).isEqualTo(200);
        } finally {
            redis.delete(List.of(limiterKey(user.username()) + ":count", limiterKey(user.username()) + ":wait"));
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void promotionRequiresFreshLoginBeforeNewAdminPermissionIsUsable() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("auth-test-promotion-admin-" + UUID.randomUUID(),
                "strong admin password 123", "ADMIN");
        AccountRow user = accounts.create("auth-test-promotion-user-" + UUID.randomUUID(),
                "strong user password 123", "USER");
        try {
            HttpClient adminClient = newClient();
            HttpClient oldUser = newClient();
            String adminToken = csrf(adminClient);
            String userToken = csrf(oldUser);
            assertThat(login(adminClient, admin, "strong admin password 123", adminToken).statusCode())
                    .isEqualTo(200);
            assertThat(login(oldUser, user, "strong user password 123", userToken).statusCode())
                    .isEqualTo(200);
            assertThat(send(oldUser, "GET", "/api/admin/users", null, null).statusCode()).isEqualTo(403);
            assertThat(send(adminClient, "PATCH", "/api/admin/users/" + user.id() + "/role",
                    "{\"role\":\"ADMIN\"}", adminToken).statusCode()).isEqualTo(200);
            assertThat(send(oldUser, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
            assertThat(send(oldUser, "GET", "/api/admin/users", null, null).statusCode()).isEqualTo(401);
            HttpClient promoted = newClient();
            HttpResponse<String> refreshedLogin = login(promoted, user, "strong user password 123", csrf(promoted));
            assertThat(refreshedLogin.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(refreshedLogin.body()).get("role").asText()).isEqualTo("ADMIN");
            assertThat(send(promoted, "GET", "/api/admin/users", null, null).statusCode()).isEqualTo(200);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", user.id(), admin.id());
        }
    }

    private String limiterKey(String username) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return "workbench:auth:login:" + HexFormat.of().formatHex(digest);
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private String csrf(HttpClient client) throws Exception {
        return mapper.readTree(send(client, "GET", "/api/auth/csrf", null, null).body()).get("token").asText();
    }

    private HttpResponse<String> login(HttpClient client, AccountRow account, String password, String token)
            throws Exception {
        return send(client, "POST", "/api/auth/login",
                json("username", account.username(), "password", password), token);
    }

    @TestConfiguration
    static class AuthClockConfiguration {
        @Bean
        @Primary
        AdjustableClock testAuthClock() {
            return new AdjustableClock();
        }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicLong now = new AtomicLong(System.currentTimeMillis());

        void reset() { now.set(System.currentTimeMillis()); }
        void set(long millis) { now.set(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    }

    private String json(String first, String firstValue, String second, String secondValue) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of(first, firstValue, second, secondValue));
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String body, String csrf)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) {
            builder.header("X-XSRF-TOKEN", csrf);
        }
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
