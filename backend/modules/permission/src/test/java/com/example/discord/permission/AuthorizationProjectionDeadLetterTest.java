package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationProjectionDeadLetterTest {
    @Test
    void storesOnlyAStableTruncatedPayloadHash() {
        AuthorizationProjectionDeadLetter first = AuthorizationProjectionDeadLetter.from("MALFORMED", "secret-body");
        AuthorizationProjectionDeadLetter second = AuthorizationProjectionDeadLetter.from("MALFORMED", "secret-body");

        assertThat(first).isEqualTo(second);
        assertThat(first.payloadHash()).hasSize(16);
        assertThat(first.payloadHash()).doesNotContain("secret");
    }
}
