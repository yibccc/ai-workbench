package com.aiworkbench.config;

import com.aiworkbench.service.InputPersistenceService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InputRecoveryRunner implements ApplicationRunner {
    private final InputPersistenceService persistence;
    private final Clock clock;

    @Autowired
    public InputRecoveryRunner(InputPersistenceService persistence) {
        this(persistence, Clock.systemUTC());
    }

    InputRecoveryRunner(InputPersistenceService persistence, Clock clock) {
        this.persistence = persistence;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        persistence.recoverExpiredProcessing(clock.instant());
    }
}
