package com.example.discord.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OperationalHardeningFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(OperationalHardeningFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC = "request_id";
    private static final String HTTP_METHOD_MDC = "http_method";
    private static final String HTTP_PATH_MDC = "http_path";
    private static final String HTTP_STATUS_MDC = "http_status";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
        "(?i)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)"
    );
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("/\\d+(?=/|$)");
    private static final Set<String> LOCAL_DEVELOPMENT_ORIGINS = Set.of(
        "http://127.0.0.1:3000",
        "http://localhost:3000"
    );

    private final MeterRegistry meterRegistry;

    public OperationalHardeningFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

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
        String requestId = requestIdFor(request.getHeader(REQUEST_ID_HEADER));
        String method = request.getMethod();
        String normalizedPath = normalizedPath(request.getRequestURI());
        long startedNanos = System.nanoTime();

        MDC.put(REQUEST_ID_MDC, requestId);
        MDC.put(HTTP_METHOD_MDC, method);
        MDC.put(HTTP_PATH_MDC, normalizedPath);
        try {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            response.setHeader("Cache-Control", "no-store");
            applyCors(request, response);

            if (HttpMethod.OPTIONS.matches(method)) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            MDC.put(HTTP_STATUS_MDC, Integer.toString(status));
            recordMetrics(method, normalizedPath, status, System.nanoTime() - startedNanos);
            logOutcome(status);
            clearMdc();
        }
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

    private void recordMetrics(String method, String path, int status, long elapsedNanos) {
        String statusValue = Integer.toString(status);
        Timer.builder("discord.api.requests")
            .description("API request latency")
            .tag("method", method)
            .tag("path", path)
            .tag("status", statusValue)
            .register(meterRegistry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);

        if (isRejected(status)) {
            Counter.builder("discord.api.rejections")
                .description("Rejected API requests")
                .tag("method", method)
                .tag("path", path)
                .tag("status", statusValue)
                .register(meterRegistry)
                .increment();
        }

        if ("POST".equals(method) && "/api/auth/login".equals(path) && (status == 401 || status == 423)) {
            Counter.builder("discord.auth.failures")
                .description("Authentication failure outcomes")
                .tag("outcome", status == 423 ? "login_locked" : "invalid_credentials")
                .register(meterRegistry)
                .increment();
        }
    }

    private static void logOutcome(int status) {
        if (isRejected(status)) {
            LOG.warn("api request rejected");
            return;
        }
        LOG.info("api request completed");
    }

    private static boolean isRejected(int status) {
        return status == 401 || status == 403 || status == 423;
    }

    private static String normalizedPath(String requestUri) {
        String withoutUuids = UUID_PATH_SEGMENT.matcher(requestUri).replaceAll("/{uuid}");
        return NUMERIC_PATH_SEGMENT.matcher(withoutUuids).replaceAll("/{number}");
    }

    private static void clearMdc() {
        MDC.remove(REQUEST_ID_MDC);
        MDC.remove(HTTP_METHOD_MDC);
        MDC.remove(HTTP_PATH_MDC);
        MDC.remove(HTTP_STATUS_MDC);
    }
}
