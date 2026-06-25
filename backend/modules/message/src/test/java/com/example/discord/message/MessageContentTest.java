package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageContentTest {
    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new MessageContent("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message content must not be blank");
    }

    @Test
    void normalizesSurroundingWhitespace() {
        MessageContent content = new MessageContent("  hello  ");

        assertThat(content.value()).isEqualTo("hello");
    }

    @Test
    void rejectsOverlongContent() {
        assertThatThrownBy(() -> new MessageContent("a".repeat(2001)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message content must not exceed 2000 characters");
    }
}
