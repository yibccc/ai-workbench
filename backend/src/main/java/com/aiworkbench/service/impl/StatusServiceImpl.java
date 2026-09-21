package com.aiworkbench.service.impl;

import com.aiworkbench.ai.DeepSeekGateway;
import com.aiworkbench.config.DeepSeekProperties;
import com.aiworkbench.dto.status.ProbeStatus;
import com.aiworkbench.dto.status.WorkbenchStatus;
import com.aiworkbench.service.StatusService;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

@Service
public class StatusServiceImpl implements StatusService {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final DeepSeekProperties deepSeekProperties;
    private final DeepSeekGateway gateway;

    public StatusServiceImpl(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            DeepSeekProperties deepSeekProperties, DeepSeekGateway gateway) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.deepSeekProperties = deepSeekProperties;
        this.gateway = gateway;
    }

    public String probe() { return gateway.probe(); }

    public WorkbenchStatus current() {
        Map<String, ProbeStatus> components = new LinkedHashMap<>();
        components.put("backend", ProbeStatus.up("Spring Boot API is responding"));
        components.put("postgres", postgresStatus());
        components.put("redis", redisStatus());
        components.put(
                "deepseek",
                deepSeekProperties.isConfigured()
                        ? ProbeStatus.notChecked("Credential configured; live call is manual")
                        : ProbeStatus.notConfigured("DEEPSEEK_API_KEY is not configured"));

        return new WorkbenchStatus(
                "ai-workbench",
                Instant.now(),
                components,
                Map.of(
                        "java", System.getProperty("java.version"),
                        "springBoot", "3.5.16",
                        "agentScope", "2.0.3"));
    }

    private ProbeStatus postgresStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2)
                    ? ProbeStatus.up("Connection accepted")
                    : ProbeStatus.down("Connection validation returned false");
        } catch (Exception exception) {
            return ProbeStatus.down(safeMessage(exception));
        }
    }

    private ProbeStatus redisStatus() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String response = connection.ping();
            return "PONG".equalsIgnoreCase(response)
                    ? ProbeStatus.up("PING returned PONG")
                    : ProbeStatus.down("Unexpected PING response");
        } catch (Exception exception) {
            return ProbeStatus.down(safeMessage(exception));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
