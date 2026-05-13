package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {
    @Test
    void normalizesEmailAddress() {
        EmailAddress email = EmailAddress.from("  User@Example.COM  ");

        assertThat(email.value()).isEqualTo("user@example.com");
        assertThat(email.toString()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsInvalidEmailAddress() {
        assertThatThrownBy(() -> EmailAddress.from("not-an-email"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email address is invalid");
    }
}
