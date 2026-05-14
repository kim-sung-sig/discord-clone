package com.example.discord.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OperationalHardeningFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Set<String> LOCAL_DEVELOPMENT_ORIGINS = Set.of(
        "http://127.0.0.1:3000",
        "http://localhost:3000"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader(REQUEST_ID_HEADER, requestIdFor(request.getHeader(REQUEST_ID_HEADER)));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cache-Control", "no-store");
        applyCors(request, response);

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void applyCors(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || !LOCAL_DEVELOPMENT_ORIGINS.contains(origin)) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Request-Id");
        response.setHeader("Vary", "Origin");
    }

    private static String requestIdFor(String incomingRequestId) {
        if (incomingRequestId != null && SAFE_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
