package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {
    @Test
    void redactsPasswordsTokensAndSecretsFromArtifactText() {
        String text = """
            POSTGRES_PASSWORD=prod-db-password-prod-db-password
            Authorization: Bearer access-token-value
            discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret
            jdbc:postgresql://db:5432/discord?password=query-password
            """;

        String redacted = SecretRedactor.redact(text);

        assertThat(redacted).doesNotContain("prod-db-password-prod-db-password");
        assertThat(redacted).doesNotContain("access-token-value");
        assertThat(redacted).doesNotContain("prod-auth-secret-prod-auth-secret");
        assertThat(redacted).doesNotContain("query-password");
        assertThat(redacted).contains("POSTGRES_PASSWORD=<redacted>");
        assertThat(redacted).contains("Authorization: Bearer <redacted>");
    }
}
