package com.aiworkbench.controller;

import com.aiworkbench.service.E2eMaintenanceService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only reset hook for the isolated d9_e2e schema. */
@RestController
@RequestMapping("/api/e2e")
@Profile("e2e & !live-acceptance")
public class E2eResetController {
    private final E2eMaintenanceService service;

    public E2eResetController(E2eMaintenanceService service) {
        this.service = service;
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        service.reset();
        return ResponseEntity.ok(Map.of("status", "RESET"));
    }
}
