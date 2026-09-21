package com.aiworkbench.events;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WorkbenchWebSocketConfig implements WebSocketConfigurer {
    private final WorkbenchEventHub hub;
    private final String[] origins;
    public WorkbenchWebSocketConfig(WorkbenchEventHub hub,
            @Value("${workbench.ws.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:15173,http://localhost:15173}") String origins) {
        this.hub = hub;
        this.origins = Arrays.stream(origins.split(",", -1)).map(String::trim).toArray(String[]::new);
        for (String origin : this.origins) {
            java.net.URI uri = java.net.URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("WebSocket origins must be explicit HTTP(S) origins without paths");
            }
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub, "/ws/events")
                .setAllowedOrigins(origins);
    }
}
