package com.aiworkbench.events;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WorkbenchWebSocketConfig implements WebSocketConfigurer {
    private final WorkbenchEventHub hub;
    public WorkbenchWebSocketConfig(WorkbenchEventHub hub) { this.hub = hub; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub, "/ws/events")
                .setAllowedOrigins("http://127.0.0.1:5173", "http://localhost:5173",
                        "http://127.0.0.1:15173", "http://localhost:15173");
    }
}
