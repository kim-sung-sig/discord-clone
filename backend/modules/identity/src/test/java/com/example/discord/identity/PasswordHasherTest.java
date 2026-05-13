package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void hashesPasswordWithoutStoringRawSecret() {
        PasswordHasher hasher = new BCryptPasswordHasher();

        String hash = hasher.hash("correct horse battery staple");

        assertThat(hash).isNotEqualTo("correct horse battery staple");
        assertThat(hasher.matches("correct horse battery staple", hash)).isTrue();
        assertThat(hasher.matches("wrong password", hash)).isFalse();
    }
}
