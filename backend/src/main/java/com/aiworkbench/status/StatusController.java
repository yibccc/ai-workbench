package com.aiworkbench.status;

import com.aiworkbench.ai.DeepSeekGateway;
import com.aiworkbench.ai.DeepSeekNotConfiguredException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final StatusService statusService;
    private final DeepSeekGateway deepSeekGateway;

    public StatusController(StatusService statusService, DeepSeekGateway deepSeekGateway) {
        this.statusService = statusService;
        this.deepSeekGateway = deepSeekGateway;
    }

    @GetMapping("/status")
    public WorkbenchStatus status() {
        return statusService.current();
    }

    @PostMapping("/ai/probe")
    public ResponseEntity<Map<String, Object>> probeAi() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "checkedAt", Instant.now(),
                    "response", deepSeekGateway.probe()));
        } catch (DeepSeekNotConfiguredException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "NOT_VERIFIED",
                    "checkedAt", Instant.now(),
                    "detail", exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "DOWN",
                    "checkedAt", Instant.now(),
                    "detail", "DeepSeek probe failed: " + exception.getClass().getSimpleName()));
        }
    }
}
