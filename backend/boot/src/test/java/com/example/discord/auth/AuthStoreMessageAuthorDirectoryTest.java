package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.PrivacySettings;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthStoreMessageAuthorDirectoryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-24T00:00:00Z");

    private final InMemoryAuthStore store = new InMemoryAuthStore();
    private final AuthStoreMessageAuthorDirectory directory = new AuthStoreMessageAuthorDirectory(store);

    @Test
    void resolvesDisplayNamesForExistingAuthorsInOneBatch() {
        UUID first = save(UserProfile.create(UUID.randomUUID(), Username.from("first_author"), "First Author", CREATED_AT));
        UUID second = save(UserProfile.create(UUID.randomUUID(), Username.from("second_author"), " 두번째 작성자 ", CREATED_AT));
        UUID deleted = UUID.randomUUID();

        Map<UUID, String> names = directory.displayNames(Set.of(first, second, deleted));

        assertThat(names).containsOnly(
            Map.entry(first, "First Author"),
            Map.entry(second, "두번째 작성자")
        );
        assertThat(names).doesNotContainKey(deleted);
    }

    @Test
    void fallsBackToUsernameWhenDisplayNameIsBlank() {
        UUID authorId = save(new UserProfile(
            UUID.randomUUID(),
            Username.from("blank_name"),
            "   ",
            CREATED_AT,
            PrivacySettings.defaults()
        ));

        assertThat(directory.displayName(authorId)).contains("blank_name");
    }

    @Test
    void returnsNothingForUnknownOrEmptyAuthorSets() {
        assertThat(directory.displayName(UUID.randomUUID())).isEmpty();
        assertThat(directory.displayNames(Set.of())).isEmpty();
    }

    @Test
    void inMemoryStoreFindsOnlyRequestedExistingProfiles() {
        UserProfile kept = UserProfile.create(UUID.randomUUID(), Username.from("kept_author"), "Kept", CREATED_AT);
        UserProfile other = UserProfile.create(UUID.randomUUID(), Username.from("other_author"), "Other", CREATED_AT);
        save(kept);
        save(other);

        assertThat(store.findProfilesByIds(Set.of(kept.id(), UUID.randomUUID()))).containsExactly(kept);
        assertThat(store.findProfilesByIds(Set.of())).isEmpty();
    }

    private UUID save(UserProfile profile) {
        EmailAddress email = EmailAddress.from(profile.username().value() + "@example.com");
        assertThat(store.saveIfAbsent(new AuthAccount(email, "hashed-password", profile))).isTrue();
        return profile.id();
    }
}
