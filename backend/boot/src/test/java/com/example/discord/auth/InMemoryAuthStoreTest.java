package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryAuthStoreTest {
    @Test
    void revokedAccessTokensAreMatchedByHash() {
        InMemoryAuthStore store = new InMemoryAuthStore();
        String token = "access-token-value";

        store.revokeAccessToken(token);

        assertThat(store.isAccessTokenRevoked(token)).isTrue();
        assertThat(store.isAccessTokenRevoked(token + "-other")).isFalse();
    }
}
