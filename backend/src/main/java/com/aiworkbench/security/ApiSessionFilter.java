package com.aiworkbench.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiSessionFilter extends OncePerRequestFilter {
    private final SessionAccess access;
    private final SessionActivity activity;
    private final ObjectMapper mapper;

    public ApiSessionFilter(SessionAccess access, SessionActivity activity, ObjectMapper mapper) {
        this.access = access;
        this.activity = activity;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/auth/login")
                || request.getRequestURI().equals("/api/auth/csrf")
                || request.getRequestURI().startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof WorkbenchPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            HttpSession session = request.getSession(false);
            if (session == null || !access.isLive(principal, session.getId())) {
                SecurityContextHolder.clearContext();
                if (session != null) {
                    activity.remove(session.getId());
                    session.invalidate();
                }
                ApiSecurity.writeProblem(mapper, request, response, HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录");
                return;
            }
        } catch (RedisConnectionFailureException exception) {
            ApiSecurity.writeProblem(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, "认证服务暂不可用");
            return;
        } catch (org.springframework.dao.DataAccessException exception) {
            ApiSecurity.writeProblem(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, "认证服务暂不可用");
            return;
        }
        chain.doFilter(request, response);
    }
}
