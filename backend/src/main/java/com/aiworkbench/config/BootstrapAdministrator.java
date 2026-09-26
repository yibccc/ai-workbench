package com.aiworkbench.config;

import com.aiworkbench.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapAdministrator {
    @Bean
    ApplicationRunner initializeFirstAdministrator(AccountService accounts,
            @Value("${workbench.auth.bootstrap-username:}") String username,
            @Value("${workbench.auth.bootstrap-password:}") String password) {
        return arguments -> {
            if (username.isEmpty() && password.isEmpty()) {
                return;
            }
            accounts.bootstrap(username, password);
        };
    }
}
