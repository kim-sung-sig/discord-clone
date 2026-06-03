package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {
    @Test
    void rejectsBlankKey() {
        assertThatThrownBy(() -> new IdempotencyKey("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("idempotency key must not be blank");
    }

    @Test
    void rejectsOverlongKey() {
        assertThatThrownBy(() -> new IdempotencyKey("a".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("idempotency key must not exceed 128 characters");
    }
}
