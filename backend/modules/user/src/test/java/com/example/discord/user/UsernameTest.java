package com.example.discord.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernameTest {
    @Test
    void normalizesUsername() {
        Username username = Username.from("  Vibe_Coder.01  ");

        assertThat(username.value()).isEqualTo("vibe_coder.01");
    }

    @Test
    void rejectsInvalidUsername() {
        assertThatThrownBy(() -> Username.from(".bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username is invalid");
    }
}
