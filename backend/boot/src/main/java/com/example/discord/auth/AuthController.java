package com.example.discord.auth;

import com.example.discord.identity.RefreshSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AuthController {
    private static final String REFRESH_COOKIE = "dc_refresh";
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    private final AuthService authService;
    private final Environment environment;

    AuthController(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    @PostMapping("/auth/signup")
    ResponseEntity<AuthResponse> signup(
        @RequestBody SignupRequest request,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        AuthResult result = authService.signup(request, userAgent);
        addRefreshCookie(httpRequest, response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @PostMapping("/auth/login")
    AuthResponse login(
        @RequestBody LoginRequest request,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        AuthResult result = authService.login(request, userAgent);
        addRefreshCookie(httpRequest, response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/auth/refresh")
    AuthResponse refresh(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        AuthResult result = authService.refresh(refreshToken);
        addRefreshCookie(request, response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(bearerToken(authorization));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logoutRefreshToken(refreshToken);
        }
        clearRefreshCookie(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/@me")
    UserResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = bearerToken(authorization);
        return authService.profileForToken(token);
    }

    @GetMapping("/admin/global-roles/audit-log")
    GlobalRoleAuditLogResponse globalRoleAuditLog(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(required = false) UUID targetUserId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return authService.globalRoleAuditLog(bearerToken(authorization), targetUserId, limit);
    }

    @GetMapping("/auth/sessions")
    SessionsResponse sessions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return authService.sessions(bearerToken(authorization));
    }

    @DeleteMapping("/auth/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @PathVariable UUID sessionId
    ) {
        authService.revokeSession(bearerToken(authorization), sessionId);
        return ResponseEntity.noContent().build();
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bearer token required");
        }
        return authorization.substring("Bearer ".length());
    }

    record SignupRequest(String email, String username, String displayName, String password) {
    }

    record LoginRequest(String email, String password) {
    }

    record AuthResponse(String accessToken, UserResponse user) {
    }

    record UserResponse(UUID id, String username, String displayName, List<String> roles, boolean admin) {
    }

    record SessionsResponse(List<SessionResponse> sessions) {
    }

    record GlobalRoleAuditLogResponse(
        List<GlobalRoleAuditEntryResponse> entries,
        GlobalRoleAuditRetentionPolicyResponse retention,
        GlobalRoleAuditExportPolicyResponse export
    ) {
    }

    record GlobalRoleAuditRetentionPolicyResponse(int maxAgeDays, Instant retainsSince) {
    }

    record GlobalRoleAuditExportPolicyResponse(List<String> formats, int maxEntriesPerRequest, String requiresRole) {
    }

    record GlobalRoleAuditEntryResponse(
        UUID targetUserId,
        String role,
        String action,
        String actor,
        String result,
        Instant occurredAt
    ) {
        static GlobalRoleAuditEntryResponse from(GlobalRoleAuditEntry entry) {
            return new GlobalRoleAuditEntryResponse(
                entry.targetUserId(),
                entry.role(),
                entry.action().name(),
                entry.actor(),
                entry.result().name(),
                entry.occurredAt()
            );
        }
    }

    record SessionResponse(
        UUID id,
        String deviceName,
        Instant createdAt,
        Instant expiresAt,
        boolean revoked
    ) {
        static SessionResponse from(RefreshSession session) {
            return new SessionResponse(
                session.id(),
                session.deviceName(),
                session.createdAt(),
                session.expiresAt(),
                session.revoked()
            );
        }
    }

    record ErrorResponse(String message) {
    }

    private void addRefreshCookie(HttpServletRequest request, HttpServletResponse response, String refreshToken) {
        writeRefreshCookie(request, response, refreshToken, REFRESH_COOKIE_MAX_AGE_SECONDS);
    }

    private void clearRefreshCookie(HttpServletRequest request, HttpServletResponse response) {
        writeRefreshCookie(request, response, "", 0);
    }

    private void writeRefreshCookie(
        HttpServletRequest request,
        HttpServletResponse response,
        String value,
        int maxAgeSeconds
    ) {
        boolean secure = secureRefreshCookie(request);
        response.addCookie(refreshCookie(value, maxAgeSeconds, secure));
        response.setHeader(HttpHeaders.SET_COOKIE, refreshCookieHeader(value, maxAgeSeconds, secure));
    }

    private static Cookie refreshCookie(String value, int maxAgeSeconds, boolean secure) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(secure);
        return cookie;
    }

    private static String refreshCookieHeader(String value, int maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(secure)
            .path("/api/auth")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite("Lax")
            .build()
            .toString();
    }

    private boolean secureRefreshCookie(HttpServletRequest request) {
        return environment.acceptsProfiles(Profiles.of("production"))
            || request.isSecure()
            || isForwardedHttps(request.getHeader("X-Forwarded-Proto"));
    }

    private static boolean isForwardedHttps(String forwardedProto) {
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return false;
        }
        return "https".equalsIgnoreCase(forwardedProto.split(",", 2)[0].trim());
    }
}

@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthControllerAdvice {
    @ExceptionHandler(LoginLockedException.class)
    ResponseEntity<AuthController.ErrorResponse> loginLocked(LoginLockedException exception) {
        return ResponseEntity.status(HttpStatus.LOCKED)
            .body(new AuthController.ErrorResponse("login temporarily locked"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<AuthController.ErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthController.ErrorResponse("invalid credentials"));
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ResponseEntity<AuthController.ErrorResponse> duplicateAccount(DuplicateAccountException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new AuthController.ErrorResponse("account already exists"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AuthController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
            .body(new AuthController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<AuthController.ErrorResponse> invalidRefresh(InvalidRefreshTokenException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthController.ErrorResponse("refresh token invalid"));
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    ResponseEntity<AuthController.ErrorResponse> refreshReuse(RefreshTokenReuseDetectedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthController.ErrorResponse("refresh token reuse detected"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<AuthController.ErrorResponse> responseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(new AuthController.ErrorResponse(exception.getReason() == null ? "request failed" : exception.getReason()));
    }
}
