package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryAuthStore {
    private final ConcurrentMap<String, AuthAccount> accountsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AuthAccount> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> revokedAccessTokens = new ConcurrentHashMap<>();

    boolean saveIfAbsent(AuthAccount account) {
        AuthAccount existing = accountsByEmail.putIfAbsent(account.email().value(), account);
        if (existing != null) {
            return false;
        }
        accountsById.put(account.profile().id(), account);
        return true;
    }

    Optional<AuthAccount> findByEmail(EmailAddress email) {
        return Optional.ofNullable(accountsByEmail.get(email.value()));
    }

    Optional<UserProfile> findById(UUID id) {
        return Optional.ofNullable(accountsById.get(id)).map(AuthAccount::profile);
    }

    void revokeAccessToken(String token) {
        revokedAccessTokens.put(token, true);
    }

    boolean isAccessTokenRevoked(String token) {
        return revokedAccessTokens.containsKey(token);
    }

    record AuthAccount(EmailAddress email, String passwordHash, UserProfile profile) {
    }
}
