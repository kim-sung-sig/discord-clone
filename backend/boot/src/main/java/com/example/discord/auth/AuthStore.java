package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import java.util.Optional;
import java.util.UUID;

interface AuthStore {
    boolean saveIfAbsent(AuthAccount account);

    Optional<AuthAccount> findByEmail(EmailAddress email);

    Optional<UserProfile> findById(UUID id);

    void revokeAccessToken(String token);

    boolean isAccessTokenRevoked(String token);
}
