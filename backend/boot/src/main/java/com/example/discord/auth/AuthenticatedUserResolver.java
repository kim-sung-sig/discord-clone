package com.example.discord.auth;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.TokenVerificationException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AuthenticatedUserResolver {
    private final AuthStore store;
    private final AccessTokenService accessTokenService;

    AuthenticatedUserResolver(AuthStore store, AccessTokenService accessTokenService) {
        this.store = store;
        this.accessTokenService = accessTokenService;
    }

    public UUID requireUserId(String authorization) {
        String token = bearerToken(authorization);
        if (store.isAccessTokenRevoked(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "access token revoked");
        }
        try {
            UUID userId = accessTokenService.verify(token).userId();
            if (store.findById(userId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found");
            }
            return userId;
        } catch (TokenVerificationException | IllegalArgumentException exception) {
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
