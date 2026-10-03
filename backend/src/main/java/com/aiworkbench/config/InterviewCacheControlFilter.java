package com.aiworkbench.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+11)
public class InterviewCacheControlFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if(path.equals("/api/interviews") || path.startsWith("/api/interviews/")) {
            response.setHeader("Cache-Control","no-store, private"); response.setHeader("X-Content-Type-Options","nosniff");
        }
        chain.doFilter(request,response);
    }
}
