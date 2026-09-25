package com.aiworkbench.events;

import com.aiworkbench.security.SessionAccess;
import com.aiworkbench.security.WorkbenchPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WorkbenchWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    static final String HTTP_SESSION_ID = "workbench.httpSessionId";
    static final String PRINCIPAL = "workbench.principal";
    static final String CSRF_TOKEN = "workbench.csrfToken";
    public static final String USER_DESTINATION = "/user/queue/workbench-events";

    private final Set<String> origins;
    private final WorkbenchSocketSessions sockets;
    private final WorkbenchStompGuard guard;

    /** STOMP adds channel executors; keep business jobs on Boot's configured application executor. */
    @Bean("applicationTaskExecutor")
    @Primary
    ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    public WorkbenchWebSocketConfig(WorkbenchSocketSessions sockets, WorkbenchStompGuard guard,
            @Value("${workbench.ws.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:15173,http://localhost:15173}") String origins) {
        this.sockets = sockets;
        this.guard = guard;
        this.origins = Set.copyOf(Arrays.stream(origins.split(",", -1)).map(String::trim).peek(origin -> {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getRawPath().isEmpty() || origin.contains("*")) {
                throw new IllegalArgumentException("WebSocket origins must be explicit HTTP(S) origins without paths");
            }
        }).toList());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/events")
                .addInterceptors(new AuthenticatedHandshake(guard.access(), origins))
                .setAllowedOrigins(origins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard.outbound());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sockets);
    }

    private static final class AuthenticatedHandshake implements HandshakeInterceptor {
        private final SessionAccess access;
        private final Set<String> origins;
        private final CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();

        private AuthenticatedHandshake(SessionAccess access, Set<String> origins) {
            this.access = access;
            this.origins = origins;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                       java.util.Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servlet)) return false;
            String origin = request.getHeaders().getOrigin();
            if (!StringUtils.hasText(origin) || !origins.contains(origin)) return false;
            HttpServletRequest http = servlet.getServletRequest();
            HttpSession session = http.getSession(false);
            Principal requestPrincipal = request.getPrincipal();
            if (session == null || !(requestPrincipal instanceof Authentication authentication)
                    || !(authentication.getPrincipal() instanceof WorkbenchPrincipal principal)
                    || !access.isLive(principal, session.getId())) return false;
            CsrfToken token = csrf.loadToken(http);
            if (token == null || !StringUtils.hasText(token.getToken())) return false;
            attributes.put(HTTP_SESSION_ID, session.getId());
            attributes.put(PRINCIPAL, principal);
            attributes.put(CSRF_TOKEN, token.getToken());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Exception exception) { }
    }
}
