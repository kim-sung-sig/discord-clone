package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.identity.RefreshSession;
import com.example.discord.user.UserProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AuthStore {
    boolean saveIfAbsent(AuthAccount account);

    Optional<AuthAccount> findByEmail(EmailAddress email);

    Optional<UserProfile> findById(UUID id);

    void revokeAccessToken(String token);

    boolean isAccessTokenRevoked(String token);

    void saveRefreshSession(RefreshSession session);

    Optional<RefreshSession> findRefreshSessionByTokenHash(String tokenHash);

    void replaceRefreshSession(RefreshSession revokedPrevious, RefreshSession next);

    List<RefreshSession> refreshSessionsForUser(UUID userId);

    boolean revokeRefreshSession(UUID userId, UUID sessionId, Instant revokedAt);

    void revokeAllRefreshSessions(UUID userId, Instant revokedAt);
}
