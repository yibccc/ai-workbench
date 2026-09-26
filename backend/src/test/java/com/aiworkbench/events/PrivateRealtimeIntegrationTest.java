package com.aiworkbench.events;

import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.security.SessionActivity;
import com.aiworkbench.security.SessionRevoker;
import com.aiworkbench.security.WorkbenchAuthentications;
import com.aiworkbench.security.WorkbenchPrincipal;
import com.aiworkbench.service.AccountService;
import com.aiworkbench.service.InputPersistenceService;
import com.aiworkbench.service.ReportPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("live-acceptance")
class PrivateRealtimeIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired WorkbenchEventHub events;
    @Autowired SessionRevoker revoker;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @Autowired SimpUserRegistry users;
    @Autowired InputPersistenceService inputs;
    @Autowired ReportPersistenceService reports;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void realStompFramesDeliverOnlyToOwnersValidSessionsAndCloseOnRevoke() throws Exception {
        AccountRow first = accounts.create("ws-a-" + UUID.randomUUID(), "strong websocket password 123", "USER");
        AccountRow second = accounts.create("ws-b-" + UUID.randomUUID(), "strong websocket password 123", "USER");
        FrameSocket firstOne = null;
        FrameSocket firstTwo = null;
        FrameSocket other = null;
        try {
            Browser a1 = login(first);
            Browser a2 = login(first);
            Browser b = login(second);
            firstOne = connect(a1);
            firstTwo = connect(a2);
            other = connect(b);
            subscribe(firstOne, first.id(), 1);
            subscribe(firstTwo, first.id(), 2);
            subscribe(other, second.id(), 1);

            UUID entity = UUID.randomUUID();
            events.publishAfterCommit(first.id(), "INPUT", entity, "SUCCEEDED");
            assertMessage(firstOne, entity);
            assertMessage(firstTwo, entity);
            assertThat(other.frames.poll(400, TimeUnit.MILLISECONDS)).isNull();

            assertThat(send(a1.client, "POST", "/api/auth/logout", "{}", a1.csrf).statusCode()).isEqualTo(200);
            assertThat(firstOne.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            UUID secondEvent = UUID.randomUUID();
            events.publishAfterCommit(first.id(), "REPORT", secondEvent, "UPDATED");
            assertMessage(firstTwo, secondEvent);
            assertThat(other.frames.poll(400, TimeUnit.MILLISECONDS)).isNull();

            revoker.revokeOlder(first.id(), first.authVersion() + 1, null);
            assertThat(firstTwo.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            events.publishAfterCommit(first.id(), "INPUT", UUID.randomUUID(), "SUCCEEDED");
            assertThat(firstTwo.frames.poll(400, TimeUnit.MILLISECONDS)).isNull();

            UUID otherEvent = UUID.randomUUID();
            events.publishAfterCommit(second.id(), "INPUT", otherEvent, "SUCCEEDED");
            assertMessage(other, otherEvent);
        } finally {
            close(firstOne);
            close(firstTwo);
            close(other);
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", first.id(), second.id());
        }
    }

    @Test
    void realStompFramesRejectBadCsrfAndForeignOrDirectSubscriptions() throws Exception {
        AccountRow user = accounts.create("ws-guard-" + UUID.randomUUID(), "strong websocket password 123", "USER");
        try {
            Browser browser = login(user);
            FrameSocket invalidCsrf = open(browser);
            invalidCsrf.socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nX-XSRF-TOKEN:wrong\n\n\0", true).join();
            assertThat(invalidCsrf.closed.get(5, TimeUnit.SECONDS)).isNotNull();

            for (String destination : new String[]{"/queue/workbench-events", "/user/other/queue/workbench-events",
                    "/user/queue/*"}) {
                FrameSocket socket = connect(browser);
                socket.socket.sendText("SUBSCRIBE\nid:bad\ndestination:" + destination + "\n\n\0", true).join();
                assertThat(socket.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            }
            FrameSocket send = connect(browser);
            send.socket.sendText("SEND\ndestination:/app/update\n\nforged\0", true).join();
            assertThat(send.closed.get(5, TimeUnit.SECONDS)).isNotNull();
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void automaticConnectSubscribeAndPushDoNotExtendIdleDeadline() throws Exception {
        clock.reset();
        AccountRow user = accounts.create("ws-idle-" + UUID.randomUUID(), "strong websocket password 123", "USER");
        FrameSocket socket = null;
        try {
            Browser browser = login(user);
            socket = connect(browser);
            subscribe(socket, user.id(), 1);
            clock.advance(Duration.ofDays(7).minusSeconds(1));
            UUID beforeDeadline = UUID.randomUUID();
            events.publishAfterCommit(user.id(), "INPUT", beforeDeadline, "PROCESSING");
            assertMessage(socket, beforeDeadline);
            clock.advance(Duration.ofSeconds(1));
            events.publishAfterCommit(user.id(), "INPUT", UUID.randomUUID(), "SUCCEEDED");
            assertThat(socket.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(socket.frames.poll(400, TimeUnit.MILLISECONDS)).isNull();
            assertThat(send(browser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        } finally {
            close(socket);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
            clock.reset();
        }
    }

    @Test
    void anonymousMissingOriginAndForeignOriginCannotHandshake() throws Exception {
        AccountRow user = accounts.create("ws-origin-" + UUID.randomUUID(), "strong websocket password 123", "USER");
        try {
            Browser authenticated = login(user);
            assertThatThrownBy(() -> open(authenticated, null)).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThatThrownBy(() -> open(authenticated, "http://evil.example"))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class);
            Browser anonymous = new Browser(HttpClient.newHttpClient(), "unused");
            assertThatThrownBy(() -> open(anonymous, "http://127.0.0.1:5173"))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(send(authenticated.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void passwordChangeClosesOldSocketButCurrentHttpSessionCanReconnect() throws Exception {
        AccountRow user = accounts.create("ws-self-change-" + UUID.randomUUID(),
                "strong websocket password 123", "USER");
        FrameSocket current = null;
        FrameSocket other = null;
        FrameSocket reconnected = null;
        try {
            Browser currentBrowser = login(user);
            Browser otherBrowser = login(user);
            current = connect(currentBrowser);
            other = connect(otherBrowser);
            subscribe(current, user.id(), 1);
            subscribe(other, user.id(), 2);

            String body = mapper.writeValueAsString(java.util.Map.of(
                    "currentPassword", "strong websocket password 123",
                    "newPassword", "new strong websocket password 123"));
            assertThat(send(currentBrowser.client, "POST", "/api/auth/password", body, currentBrowser.csrf)
                    .statusCode()).isEqualTo(200);
            assertThat(current.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(other.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(send(currentBrowser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
            assertThat(send(otherBrowser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);

            reconnected = connect(currentBrowser);
            subscribe(reconnected, user.id(), 1);
            UUID entity = UUID.randomUUID();
            events.publishAfterCommit(user.id(), "REPORT", entity, "SUCCEEDED");
            assertMessage(reconnected, entity);
        } finally {
            close(current);
            close(other);
            close(reconnected);
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    @Test
    void inputAndReportPersistenceNotifyOnlyAfterCommitAndNeverAfterRollback() throws Exception {
        clock.reset();
        AccountRow owner = accounts.create("ws-persist-owner-" + UUID.randomUUID(),
                "strong websocket password 123", "USER");
        AccountRow other = accounts.create("ws-persist-other-" + UUID.randomUUID(),
                "strong websocket password 123", "USER");
        FrameSocket ownedSocket = null;
        FrameSocket otherSocket = null;
        UUID committedInput = null;
        UUID committedReport = null;
        try {
            ownedSocket = connect(login(owner));
            otherSocket = connect(login(other));
            subscribe(ownedSocket, owner.id(), 1);
            subscribe(otherSocket, other.id(), 1);
            SecurityContextHolder.getContext().setAuthentication(WorkbenchAuthentications.authentication(
                    new WorkbenchPrincipal(owner.id(), owner.role(), owner.authVersion())));
            TransactionTemplate transaction = new TransactionTemplate(transactions);
            FrameSocket recipient = ownedSocket;
            String inputRequest = "ws-input-" + UUID.randomUUID();
            UUID inputId = transaction.execute(status -> {
                UUID id = inputs.createOrGet(inputRequest, "persisted capture input", Instant.now(),
                        ZoneId.of("Asia/Shanghai")).row().id();
                assertThat(recipient.frames.poll()).isNull();
                return id;
            });
            committedInput = inputId;
            assertMessage(ownedSocket, inputId);
            assertThat(otherSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM capture_inputs WHERE id=?", Long.class, inputId))
                    .isEqualTo(1L);

            UUID rolledBackInput = transaction.execute(status -> {
                UUID id = inputs.createOrGet("ws-input-rollback-" + UUID.randomUUID(),
                        "rolled-back capture input", Instant.now(), ZoneId.of("Asia/Shanghai")).row().id();
                status.setRollbackOnly();
                return id;
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM capture_inputs WHERE id=?", Long.class,
                    rolledBackInput)).isZero();
            assertThat(ownedSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();

            UUID reportRequest = UUID.randomUUID();
            LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            UUID reportId = transaction.execute(status -> {
                UUID id = reports.prepare(reportRequest, "DAILY", date, date.plusDays(1),
                        ZoneId.of("Asia/Shanghai")).row().id();
                assertThat(recipient.frames.poll()).isNull();
                return id;
            });
            committedReport = reportId;
            assertMessage(ownedSocket, reportId);
            assertThat(otherSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE id=?", Long.class, reportId))
                    .isEqualTo(1L);

            UUID rolledBackReport = transaction.execute(status -> {
                UUID id = reports.prepare(UUID.randomUUID(), "DAILY", date.plusDays(1),
                        date.plusDays(2), ZoneId.of("Asia/Shanghai")).row().id();
                status.setRollbackOnly();
                return id;
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE id=?", Long.class,
                    rolledBackReport)).isZero();
            assertThat(ownedSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(otherSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            SecurityContextHolder.clearContext();
            close(ownedSocket);
            close(otherSocket);
            if (committedReport != null) jdbc.update("DELETE FROM reports WHERE id=?", committedReport);
            if (committedInput != null) jdbc.update("DELETE FROM capture_inputs WHERE id=?", committedInput);
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", owner.id(), other.id());
        }
    }

    @Test
    void adminHttpMutationsCloseOldSocketsAndRequireFreshUserAuthentication() throws Exception {
        clock.reset();
        AccountRow admin = accounts.create("ws-admin-" + UUID.randomUUID(),
                "strong websocket password 123", "ADMIN");
        AccountRow target = accounts.create("ws-target-" + UUID.randomUUID(),
                "strong websocket password 123", "USER");
        FrameSocket beforeDisable = null;
        FrameSocket beforeRole = null;
        FrameSocket beforeReset = null;
        FrameSocket fresh = null;
        try {
            Browser adminBrowser = login(admin);
            Browser disabledBrowser = login(target);
            beforeDisable = connect(disabledBrowser);
            subscribe(beforeDisable, target.id(), 1);
            String base = "/api/admin/users/" + target.id();
            assertThat(send(adminBrowser.client, "PATCH", base + "/enabled", "{\"enabled\":false}",
                    adminBrowser.csrf).statusCode()).isEqualTo(200);
            assertThat(beforeDisable.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            events.publishAfterCommit(target.id(), "INPUT", UUID.randomUUID(), "SUCCEEDED");
            assertThat(beforeDisable.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(send(disabledBrowser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);

            assertThat(send(adminBrowser.client, "PATCH", base + "/enabled", "{\"enabled\":true}",
                    adminBrowser.csrf).statusCode()).isEqualTo(200);
            Browser roleBrowser = login(target);
            beforeRole = connect(roleBrowser);
            subscribe(beforeRole, target.id(), 1);
            assertThat(send(adminBrowser.client, "PATCH", base + "/role", "{\"role\":\"ADMIN\"}",
                    adminBrowser.csrf).statusCode()).isEqualTo(200);
            assertThat(beforeRole.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            events.publishAfterCommit(target.id(), "REPORT", UUID.randomUUID(), "UPDATED");
            assertThat(beforeRole.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(send(roleBrowser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);

            Browser resetBrowser = login(target);
            beforeReset = connect(resetBrowser);
            subscribe(beforeReset, target.id(), 1);
            assertThat(send(adminBrowser.client, "POST", base + "/reset-password",
                    "{\"password\":\"new strong websocket password 123\"}", adminBrowser.csrf)
                    .statusCode()).isEqualTo(200);
            assertThat(beforeReset.closed.get(5, TimeUnit.SECONDS)).isNotNull();
            events.publishAfterCommit(target.id(), "INPUT", UUID.randomUUID(), "SUCCEEDED");
            assertThat(beforeReset.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(send(resetBrowser.client, "GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);

            Browser newBrowser = login(target, "new strong websocket password 123");
            fresh = connect(newBrowser);
            subscribe(fresh, target.id(), 1);
            UUID entity = UUID.randomUUID();
            events.publishAfterCommit(target.id(), "REPORT", entity, "SUCCEEDED");
            assertMessage(fresh, entity);
        } finally {
            close(beforeDisable);
            close(beforeRole);
            close(beforeReset);
            close(fresh);
            jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)", target.id(), admin.id());
        }
    }

    private Browser login(AccountRow account) throws Exception {
        return login(account, "strong websocket password 123");
    }

    private Browser login(AccountRow account, String password) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String csrf = mapper.readTree(send(client, "GET", "/api/auth/csrf", null, null).body())
                .get("token").asText();
        String body = mapper.writeValueAsString(java.util.Map.of("username", account.username(),
                "password", password));
        assertThat(send(client, "POST", "/api/auth/login", body, csrf).statusCode()).isEqualTo(200);
        return new Browser(client, csrf);
    }

    private FrameSocket connect(Browser browser) throws Exception {
        FrameSocket frames = open(browser);
        frames.socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nX-XSRF-TOKEN:" + browser.csrf + "\n\n\0", true).join();
        assertThat(frames.frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
        return frames;
    }

    private FrameSocket open(Browser browser) throws Exception {
        return open(browser, "http://127.0.0.1:5173");
    }

    private FrameSocket open(Browser browser, String origin) throws Exception {
        FrameListener listener = new FrameListener();
        WebSocket.Builder builder = browser.client.newWebSocketBuilder().subprotocols("v12.stomp")
                .connectTimeout(Duration.ofSeconds(5));
        if (origin != null) builder.header("Origin", origin);
        WebSocket socket = builder
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/events"), listener)
                .get(5, TimeUnit.SECONDS);
        return new FrameSocket(socket, listener.frames, listener.closed);
    }

    private void subscribe(FrameSocket frames, UUID userId, int expected) throws Exception {
        frames.socket.sendText("SUBSCRIBE\nid:events\ndestination:/user/queue/workbench-events\n\n\0", true).join();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SimpUser user = users.getUser(userId.toString());
            long subscriptions = user == null ? 0 : user.getSessions().stream()
                    .flatMap(session -> session.getSubscriptions().stream()).count();
            if (subscriptions >= expected) return;
            Thread.sleep(10);
        }
        throw new AssertionError("STOMP subscription did not reach the broker");
    }

    private void assertMessage(FrameSocket socket, UUID entity) throws Exception {
        String frame = socket.frames.poll(5, TimeUnit.SECONDS);
        assertThat(frame).startsWith("MESSAGE").contains(entity.toString());
        String payload = frame.substring(frame.indexOf("\n\n") + 2);
        assertThat(payload).doesNotContain("\"content\"", "\"apiKey\"");
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String body, String csrf)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void close(FrameSocket socket) {
        if (socket != null) socket.socket.abort();
    }

    private record Browser(HttpClient client, String csrf) { }
    private record FrameSocket(WebSocket socket, BlockingQueue<String> frames,
                               CompletableFuture<Integer> closed) { }

    private static final class FrameListener implements WebSocket.Listener {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder pending = new StringBuilder();

        @Override public void onOpen(WebSocket socket) { socket.request(1); }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            pending.append(text);
            int end;
            while ((end = pending.indexOf("\0")) >= 0) {
                String frame = pending.substring(0, end).stripLeading();
                pending.delete(0, end + 1);
                if (!frame.isEmpty()) frames.add(frame);
            }
            socket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock realtimeClock() { return new MutableClock(); }
    }

    static final class MutableClock extends Clock {
        private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
        void reset() { now.set(System.currentTimeMillis()); }
        void advance(Duration duration) { now.addAndGet(duration.toMillis()); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    }
}
