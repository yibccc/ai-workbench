package com.aiworkbench.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Apply before security too: unauthorized metadata and byte requests are never cached. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CommunityCacheControlFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.startsWith("/api/community/") || path.startsWith("/api/me/posts")
                || path.startsWith("/api/me/community/") || path.startsWith("/api/admin/community/")) {
            response.setHeader("Cache-Control", "no-store, private");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        chain.doFilter(request, response);
    }
}
