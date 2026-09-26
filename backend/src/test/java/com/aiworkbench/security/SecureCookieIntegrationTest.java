package com.aiworkbench.security;

import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
class SecureCookieIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void productionCookieSettingAppearsOnActualCsrfAndSessionSetCookieHeaders() throws Exception {
        AccountRow user = accounts.create("auth-test-secure-" + UUID.randomUUID(),
                "strong secure password 123", "USER");
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(csrf.statusCode()).isEqualTo(200);
            String csrfHeader = csrf.headers().allValues("Set-Cookie").stream()
                    .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                    .findFirst().orElseThrow();
            assertThat(csrfHeader.toLowerCase(Locale.ROOT)).contains("secure", "samesite=lax");
            String csrfCookie = csrfHeader.substring(0, csrfHeader.indexOf(';'));
            String token = mapper.readTree(csrf.body()).get("token").asText();

            HttpRequest loginRequest = HttpRequest.newBuilder(uri("/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", csrfCookie)
                    .header("X-XSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
                            java.util.Map.of("username", user.username(),
                                    "password", "strong secure password 123")), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> loggedIn = client.send(loginRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(loggedIn.statusCode()).isEqualTo(200);
            List<String> cookies = loggedIn.headers().allValues("Set-Cookie");
            String session = cookies.stream().filter(cookie -> cookie.startsWith("WORKBENCH_SESSION="))
                    .findFirst().orElseThrow();
            assertThat(session.toLowerCase(Locale.ROOT)).contains("secure", "httponly", "samesite=lax");
        } finally {
            jdbc.update("DELETE FROM user_accounts WHERE id=?", user.id());
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
