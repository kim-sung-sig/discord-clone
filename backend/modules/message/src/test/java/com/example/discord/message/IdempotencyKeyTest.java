package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
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
    void normalizesSurroundingWhitespace() {
        assertThat(new IdempotencyKey("  send-1  ").value()).isEqualTo("send-1");
    }

    @Test
    void rejectsOverlongKey() {
        assertThatThrownBy(() -> new IdempotencyKey("a".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("idempotency key must not exceed 128 characters");
    }
}
