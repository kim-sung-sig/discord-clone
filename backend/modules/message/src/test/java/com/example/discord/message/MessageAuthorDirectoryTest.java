package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageAuthorDirectoryTest {
    @Test
    void singleLookupAsksOnlyForTheRequestedAuthor() {
        UUID authorId = UUID.randomUUID();
        List<Set<UUID>> requests = new ArrayList<>();
        MessageAuthorDirectory directory = userIds -> {
            requests.add(userIds);
            return Map.of(authorId, "Display Name");
        };

        assertThat(directory.displayName(authorId)).contains("Display Name");
        assertThat(requests).containsExactly(Set.of(authorId));
    }

    @Test
    void singleLookupIsEmptyWhenTheAuthorNoLongerExists() {
        MessageAuthorDirectory directory = userIds -> Map.of();

        assertThat(directory.displayName(UUID.randomUUID())).isEmpty();
    }

    @Test
    void singleLookupRejectsMissingUserId() {
        MessageAuthorDirectory directory = userIds -> Map.of();

        assertThatThrownBy(() -> directory.displayName(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("userId must not be null");
    }
}
