package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageReadModelMentionTokenTest {
    @Test
    void convertsAllMentionTargetsToTokensInInputOrder() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        List<MessageMentionTarget> mentions = new ArrayList<>(List.of(
            new UserMentionTarget(userId), new RoleMentionTarget(roleId), new ChannelMentionTarget(channelId)
        ));
        List<String> expectedTokens = new ArrayList<>(List.of(userId.toString(), roleId.toString(), channelId.toString()));
        for (SpecialMentionKind kind : SpecialMentionKind.values()) {
            mentions.add(new SpecialMentionTarget(kind));
            expectedTokens.add(kind.name().toLowerCase(Locale.ROOT));
        }
        Message message = new Message(
            UUID.randomUUID(), new UserMessageAuthor(UUID.randomUUID()), new ChannelMessageTarget(UUID.randomUUID(), channelId),
            new MessageContent("hello"), mentions, false, false, List.of(), Instant.EPOCH, Instant.EPOCH
        );

        assertThat(MessageReadModel.from(message, 1L).mentions()).containsExactlyElementsOf(expectedTokens);
    }
}
