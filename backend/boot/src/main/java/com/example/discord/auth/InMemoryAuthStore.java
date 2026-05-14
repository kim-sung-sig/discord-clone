package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
class InMemoryAuthStore implements AuthStore {
    private final ConcurrentMap<String, AuthAccount> accountsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AuthAccount> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> revokedAccessTokens = new ConcurrentHashMap<>();

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
        revokedAccessTokens.put(token, true);
    }

    @Override
    public boolean isAccessTokenRevoked(String token) {
        return revokedAccessTokens.containsKey(token);
    }
}
