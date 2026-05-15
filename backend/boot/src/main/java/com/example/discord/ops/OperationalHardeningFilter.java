package com.example.discord.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
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
    private static final Pattern INVITE_ACCEPT_PATH = Pattern.compile("^/api/invites/[^/]+/accept$");
    private static final Pattern LOCAL_DEVELOPMENT_ORIGIN = Pattern.compile(
        "^http://(127\\.0\\.0\\.1|localhost):\\d{2,5}$"
    );

    private final MeterRegistry meterRegistry;
    private final RateLimitStore rateLimitStore;

    public OperationalHardeningFilter(MeterRegistry meterRegistry, RateLimitStore rateLimitStore) {
        this.meterRegistry = meterRegistry;
        this.rateLimitStore = rateLimitStore;
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

            Optional<RateLimitPolicy> policy = ApiRateLimitPolicy.forRequest(method, normalizedPath);
            if (policy.isPresent()) {
                RateLimitDecision decision = rateLimitStore.consume(
                    new RateLimitKey(policy.get().id(), rateLimitSubjectFor(request, policy.get())),
                    policy.get(),
                    Instant.now()
                );
                if (!decision.allowed()) {
                    writeRateLimitResponse(response, decision);
                    return;
                }
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
        if (origin == null || !LOCAL_DEVELOPMENT_ORIGIN.matcher(origin).matches()) {
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
        if (INVITE_ACCEPT_PATH.matcher(requestUri).matches()) {
            return "/api/invites/{token}/accept";
        }
        String withoutUuids = UUID_PATH_SEGMENT.matcher(requestUri).replaceAll("/{uuid}");
        return NUMERIC_PATH_SEGMENT.matcher(withoutUuids).replaceAll("/{number}");
    }

    private static String rateLimitSubjectFor(HttpServletRequest request, RateLimitPolicy policy) {
        if ("auth-login".equals(policy.id())) {
            return ipSubject(request);
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return "token:" + sha256(authorization.substring("Bearer ".length()));
        }
        return ipSubject(request);
    }

    private static String ipSubject(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
            ? request.getRemoteAddr()
            : forwardedFor.split(",", 2)[0].trim();
        return "ip:" + sha256(ip);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static void writeRateLimitResponse(HttpServletResponse response, RateLimitDecision decision) throws IOException {
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.getWriter().write("{\"message\":\"rate limit exceeded\"}");
    }

    private static void clearMdc() {
        MDC.remove(REQUEST_ID_MDC);
        MDC.remove(HTTP_METHOD_MDC);
        MDC.remove(HTTP_PATH_MDC);
        MDC.remove(HTTP_STATUS_MDC);
    }
}
