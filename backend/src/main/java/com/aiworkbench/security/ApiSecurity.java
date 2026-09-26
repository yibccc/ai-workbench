package com.aiworkbench.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class ApiSecurity {
    @Bean
    FilterRegistrationBean<ApiSessionFilter> apiSessionFilterRegistration(ApiSessionFilter filter) {
        FilterRegistrationBean<ApiSessionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    /** Prevent Boot from creating a second, generated-password identity channel. */
    @Bean
    AuthenticationManager disabledFrameworkAuthentication() {
        return authentication -> { throw new BadCredentialsException("Use application session login"); };
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiSessionFilter sessionFilter,
                                               ObjectMapper mapper,
                                               @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookie).sameSite("Lax"));
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .securityContext(context -> context.securityContextRepository(
                        new HttpSessionSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/e2e/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(mapper, request, response, HttpStatus.UNAUTHORIZED, "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(mapper, request, response, HttpStatus.FORBIDDEN,
                                        exception instanceof org.springframework.security.web.csrf.CsrfException
                                                ? "CSRF 凭证无效" : "无权执行此操作")))
                .addFilterBefore(sessionFilter, AuthorizationFilter.class);
        return http.build();
    }

    static void writeProblem(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setInstance(java.net.URI.create(request.getRequestURI()));
        mapper.writeValue(response.getOutputStream(), detail);
    }
}
