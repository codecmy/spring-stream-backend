package com.example.spring_stream_backend.filter;

import com.example.spring_stream_backend.auth.HankoSessionValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HankoSessionFilter extends OncePerRequestFilter {
    private final HankoSessionValidator validator;
    public HankoSessionFilter(HankoSessionValidator validator) {
        this.validator = validator;
    }

@Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (HttpMethod.GET.matches(method)) {
            if (path.equals("/api/v1/videos") || path.startsWith("/api/v1/videos/")) {
                return true;
            }
        }
        if (path.equals("/api/health") || path.equals("/health") || path.equals("/actuator/health")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException, IOException, ServletException {
        applyCorsHeaders(request, response);

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.OK.value());
            return;
        }

        if (!validator.isSessionValid(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return;
        }

        boolean allowed = origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:");
        if (!allowed) {
            return;
        }

        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Range,Content-Type,Accept,Origin,Authorization");
        response.setHeader("Access-Control-Expose-Headers", "Content-Range,Content-Length,Accept-Ranges,Content-Type");
    }
}
