package com.example.discord.auth;

import com.example.discord.identity.BearerTokenVerifier;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AuthenticatedUserResolver {
    private final AuthStore store;
    private final BearerTokenVerifier bearerTokenVerifier;

    AuthenticatedUserResolver(AuthStore store, BearerTokenVerifier bearerTokenVerifier) {
        this.store = store;
        this.bearerTokenVerifier = bearerTokenVerifier;
    }

    public UUID requireUserId(String authorization) {
        String token = bearerToken(authorization);
        if (store.isAccessTokenRevoked(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token revoked");
        }
        try {
            UUID userId = bearerTokenVerifier.requireUserId(authorization);
            if (store.findById(userId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found");
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token invalid", exception);
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, HttpHeaders.AUTHORIZATION + " bearer token required");
        }
        return authorization.substring("Bearer ".length());
    }
}
