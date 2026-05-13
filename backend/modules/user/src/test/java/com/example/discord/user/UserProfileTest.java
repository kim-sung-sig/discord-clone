package com.example.discord.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProfileTest {
    @Test
    void createsProfileWithDefaultPrivacySettings() {
        UserProfile profile = UserProfile.create(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            Username.from("vibe_coder"),
            "Vibe Coder",
            Instant.parse("2026-05-13T00:00:00Z")
        );

        assertThat(profile.id()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(profile.username().value()).isEqualTo("vibe_coder");
        assertThat(profile.privacy().allowFriendRequests()).isTrue();
        assertThat(profile.privacy().allowDirectMessagesFromMutualGuildMembers()).isTrue();
    }
}
