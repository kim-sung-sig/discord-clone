package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.identity.RefreshSession;
import com.example.discord.user.UserProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres & !production & !admin-cli")
class InMemoryAuthStore implements AuthStore {
    private final ConcurrentMap<String, AuthAccount> accountsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AuthAccount> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> revokedAccessTokens = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RefreshSession> refreshSessionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> refreshSessionIdsByTokenHash = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, java.util.Set<String>> globalRolesByUserId = new ConcurrentHashMap<>();
    private final List<GlobalRoleAuditEntry> globalRoleAuditLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @Override
    public boolean saveIfAbsent(AuthAccount account) {
        AuthAccount existing = accountsByEmail.putIfAbsent(account.email().value(), account);
        if (existing != null) {
            return false;
        }
        accountsById.put(account.profile().id(), account);
        return true;
    }

    @Override
    public Optional<AuthAccount> findByEmail(EmailAddress email) {
        return Optional.ofNullable(accountsByEmail.get(email.value()));
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return Optional.ofNullable(accountsById.get(id)).map(AuthAccount::profile);
    }

    @Override
    public void revokeAccessToken(String token) {
        revokedAccessTokens.put(tokenHash(token), true);
    }

    @Override
    public boolean isAccessTokenRevoked(String token) {
        return revokedAccessTokens.containsKey(tokenHash(token));
    }

    @Override
    public void saveRefreshSession(RefreshSession session) {
        refreshSessionsById.put(session.id(), session);
        refreshSessionIdsByTokenHash.put(session.tokenHash(), session.id());
    }

    @Override
    public Optional<RefreshSession> findRefreshSessionByTokenHash(String tokenHash) {
        UUID sessionId = refreshSessionIdsByTokenHash.get(tokenHash);
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(refreshSessionsById.get(sessionId));
    }

    @Override
    public void replaceRefreshSession(RefreshSession revokedPrevious, RefreshSession next) {
        saveRefreshSession(revokedPrevious);
        saveRefreshSession(next);
    }

    @Override
    public List<RefreshSession> refreshSessionsForUser(UUID userId) {
        return refreshSessionsById.values().stream()
            .filter(session -> session.userId().equals(userId))
            .sorted(Comparator.comparing(RefreshSession::createdAt))
            .toList();
    }

    @Override
    public boolean revokeRefreshSession(UUID userId, UUID sessionId, Instant revokedAt) {
        RefreshSession session = refreshSessionsById.get(sessionId);
        if (session == null || !session.userId().equals(userId)) {
            return false;
        }
        saveRefreshSession(session.revoke(revokedAt));
        return true;
    }

    @Override
    public void revokeAllRefreshSessions(UUID userId, Instant revokedAt) {
        refreshSessionsForUser(userId)
            .forEach(session -> saveRefreshSession(session.revoke(revokedAt)));
    }

    @Override
    public boolean grantGlobalRole(UUID userId, String role) {
        String canonicalRole = GlobalRole.canonical(role);
        return globalRolesByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(canonicalRole);
    }

    @Override
    public boolean revokeGlobalRole(UUID userId, String role) {
        java.util.Set<String> roles = globalRolesByUserId.get(userId);
        return roles != null && roles.remove(GlobalRole.canonical(role));
    }

    @Override
    public List<String> globalRolesForUser(UUID userId) {
        return globalRolesByUserId.getOrDefault(userId, java.util.Set.of()).stream()
            .sorted()
            .toList();
    }

    @Override
    public void recordGlobalRoleAudit(GlobalRoleAuditEntry entry) {
        globalRoleAuditLog.add(entry);
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(UUID userId) {
        synchronized (globalRoleAuditLog) {
            return globalRoleAuditLog.stream()
                .filter(entry -> entry.targetUserId().equals(userId))
                .toList();
        }
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(UUID userId, int limit) {
        synchronized (globalRoleAuditLog) {
            return globalRoleAuditLog.stream()
                .filter(entry -> entry.targetUserId().equals(userId))
                .sorted(Comparator.comparing(GlobalRoleAuditEntry::occurredAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
        }
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(int limit) {
        synchronized (globalRoleAuditLog) {
            return globalRoleAuditLog.stream()
                .sorted(Comparator.comparing(GlobalRoleAuditEntry::occurredAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
        }
    }

    private static String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
