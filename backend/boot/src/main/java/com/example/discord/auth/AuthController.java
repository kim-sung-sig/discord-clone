package com.example.discord.auth;

import com.example.discord.identity.RefreshSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AuthController {
    private static final String REFRESH_COOKIE = "dc_refresh";
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    ResponseEntity<AuthResponse> signup(
        @RequestBody SignupRequest request,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
        HttpServletResponse response
    ) {
        AuthResult result = authService.signup(request, userAgent);
        addRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @PostMapping("/auth/login")
    AuthResponse login(
        @RequestBody LoginRequest request,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
        HttpServletResponse response
    ) {
        AuthResult result = authService.login(request, userAgent);
        addRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/auth/refresh")
    AuthResponse refresh(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        AuthResult result = authService.refresh(refreshToken);
        addRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletResponse response
    ) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(bearerToken(authorization));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logoutRefreshToken(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/@me")
    UserResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = bearerToken(authorization);
        return authService.profileForToken(token);
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

    record UserResponse(UUID id, String username, String displayName) {
    }

    record SessionsResponse(List<SessionResponse> sessions) {
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

    private static void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }

    private static void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
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
}
