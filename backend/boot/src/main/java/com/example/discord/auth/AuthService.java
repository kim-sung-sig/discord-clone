package com.example.discord.auth;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.EmailAddress;
import com.example.discord.identity.LoginFailureTracker;
import com.example.discord.identity.PasswordHasher;
import com.example.discord.identity.RefreshSession;
import com.example.discord.identity.TokenVerificationException;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final int GLOBAL_ROLE_AUDIT_RETENTION_DAYS = 365;
    private static final int GLOBAL_ROLE_AUDIT_MAX_EXPORT_ENTRIES = 100;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthStore store;
    private final PasswordHasher passwordHasher;
    private final AccessTokenService accessTokenService;
    private final LoginFailureTracker loginFailures;
    private final Clock clock;

    AuthService(
        AuthStore store,
        PasswordHasher passwordHasher,
        AccessTokenService accessTokenService,
        LoginFailureTracker loginFailures,
        Clock clock
    ) {
        this.store = store;
        this.passwordHasher = passwordHasher;
        this.accessTokenService = accessTokenService;
        this.loginFailures = loginFailures;
        this.clock = clock;
    }

    AuthResult signup(AuthController.SignupRequest request, String deviceName) {
        EmailAddress email = EmailAddress.from(request.email());
        UserProfile profile = UserProfile.create(
            UUID.randomUUID(),
            Username.from(request.username()),
            request.displayName(),
            clock.instant()
        );
        AuthAccount account = new AuthAccount(
            email,
            passwordHasher.hash(request.password()),
            profile
        );
        if (!store.saveIfAbsent(account)) {
            throw new DuplicateAccountException();
        }
        return authResult(profile, deviceName);
    }

    AuthResult login(AuthController.LoginRequest request, String deviceName) {
        EmailAddress email = EmailAddress.from(request.email());
        if (loginFailures.isLocked(email)) {
            throw new LoginLockedException();
        }

        AuthAccount account = store.findByEmail(email).orElse(null);
        if (account == null) {
            recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }
        if (!passwordHasher.matches(request.password(), account.passwordHash())) {
            recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }

        loginFailures.clear(email);
        logSuspiciousLoginCandidate(account.profile().id(), deviceName);
        return authResult(account.profile(), deviceName);
    }

    void logout(String token) {
        store.revokeAccessToken(token);
    }

    void logoutRefreshToken(String refreshToken) {
        store.findRefreshSessionByTokenHash(tokenHash(refreshToken))
            .ifPresent(session -> store.revokeRefreshSession(session.userId(), session.id(), clock.instant()));
    }

    AuthResult refresh(String refreshToken) {
        RefreshSession session = store.findRefreshSessionByTokenHash(tokenHash(refreshToken))
            .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (session.revoked()) {
            store.revokeAllRefreshSessions(session.userId(), now);
            throw new RefreshTokenReuseDetectedException();
        }
        if (session.expiredAt(now)) {
            store.revokeRefreshSession(session.userId(), session.id(), now);
            throw new InvalidRefreshTokenException();
        }
        UserProfile profile = store.findById(session.userId())
            .orElseThrow(InvalidRefreshTokenException::new);
        String nextRefreshToken = newRefreshToken();
        RefreshSession.Rotation rotation = session.rotate(
            UUID.randomUUID(),
            tokenHash(nextRefreshToken),
            now,
            now.plus(REFRESH_TOKEN_TTL)
        );
        store.replaceRefreshSession(rotation.revokedPrevious(), rotation.next());
        return new AuthResult(authResponse(profile), nextRefreshToken);
    }

    AuthController.SessionsResponse sessions(String token) {
        UUID userId = userIdForToken(token);
        List<AuthController.SessionResponse> sessions = store.refreshSessionsForUser(userId).stream()
            .map(AuthController.SessionResponse::from)
            .toList();
        return new AuthController.SessionsResponse(sessions);
    }

    void revokeSession(String token, UUID sessionId) {
        UUID userId = userIdForToken(token);
        if (!store.revokeRefreshSession(userId, sessionId, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
    }

    AuthController.UserResponse profileForToken(String token) {
        UUID userId = userIdForToken(token);
        return store.findById(userId)
            .map(profile -> userResponse(profile, store.globalRolesForUser(profile.id())))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }

    AuthController.GlobalRoleAuditLogResponse globalRoleAuditLog(String token, UUID targetUserId, int limit) {
        UUID requesterId = userIdForToken(token);
        if (!store.globalRolesForUser(requesterId).contains(GlobalRole.SECURITY_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        int boundedLimit = Math.min(Math.max(limit, 1), GLOBAL_ROLE_AUDIT_MAX_EXPORT_ENTRIES);
        Instant retentionCutoff = clock.instant().minus(Duration.ofDays(GLOBAL_ROLE_AUDIT_RETENTION_DAYS));
        List<GlobalRoleAuditEntry> entries = targetUserId == null
            ? store.globalRoleAuditLog(boundedLimit)
            : store.globalRoleAuditLog(targetUserId, boundedLimit);
        return new AuthController.GlobalRoleAuditLogResponse(entries.stream()
            .filter(entry -> !entry.occurredAt().isBefore(retentionCutoff))
            .map(AuthController.GlobalRoleAuditEntryResponse::from)
            .toList(),
            new AuthController.GlobalRoleAuditRetentionPolicyResponse(
                GLOBAL_ROLE_AUDIT_RETENTION_DAYS,
                retentionCutoff
            ),
            new AuthController.GlobalRoleAuditExportPolicyResponse(
                List.of("json"),
                GLOBAL_ROLE_AUDIT_MAX_EXPORT_ENTRIES,
                GlobalRole.SECURITY_ADMIN
            ));
    }

    private UUID userIdForToken(String token) {
        if (store.isAccessTokenRevoked(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token revoked");
        }
        try {
            return accessTokenService.verify(token).userId();
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token invalid", exception);
        }
    }

    private void recordLoginFailure(EmailAddress email) {
        loginFailures.recordFailure(email);
        if (loginFailures.isLocked(email)) {
            throw new LoginLockedException();
        }
    }

    private AuthController.AuthResponse authResponse(UserProfile profile) {
        return new AuthController.AuthResponse(accessTokenService.issue(profile.id()), userResponse(profile, store.globalRolesForUser(profile.id())));
    }

    private AuthResult authResult(UserProfile profile, String deviceName) {
        String refreshToken = newRefreshToken();
        Instant now = clock.instant();
        store.saveRefreshSession(RefreshSession.create(
            UUID.randomUUID(),
            profile.id(),
            tokenHash(refreshToken),
            safeDeviceName(deviceName),
            now,
            now.plus(REFRESH_TOKEN_TTL)
        ));
        return new AuthResult(authResponse(profile), refreshToken);
    }

    private void logSuspiciousLoginCandidate(UUID userId, String deviceName) {
        String normalizedDevice = safeDeviceName(deviceName);
        boolean knownDevice = store.refreshSessionsForUser(userId).stream()
            .filter(session -> !session.revoked())
            .anyMatch(session -> session.deviceName().equals(normalizedDevice));
        if (!knownDevice) {
            LOG.warn("auth suspicious login detected user_id={} device_name={}", userId, normalizedDevice);
        }
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private static String safeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "Unknown device";
        }
        String normalized = deviceName.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static AuthController.UserResponse userResponse(UserProfile profile, List<String> roles) {
        return new AuthController.UserResponse(
            profile.id(),
            profile.username().value(),
            profile.displayName(),
            roles,
            roles.contains(GlobalRole.SECURITY_ADMIN)
        );
    }
}

final class GlobalRole {
    static final String SECURITY_ADMIN = "SECURITY_ADMIN";

    private GlobalRole() {
    }

    static String canonical(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("global role is required");
        }
        String canonical = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (!canonical.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("global role is invalid");
        }
        return canonical;
    }
}

record AuthResult(AuthController.AuthResponse response, String refreshToken) {
}

class InvalidCredentialsException extends RuntimeException {
}

class LoginLockedException extends RuntimeException {
}

class DuplicateAccountException extends RuntimeException {
}

class InvalidRefreshTokenException extends RuntimeException {
}

class RefreshTokenReuseDetectedException extends RuntimeException {
}
