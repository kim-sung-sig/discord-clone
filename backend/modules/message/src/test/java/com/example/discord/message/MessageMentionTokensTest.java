package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageMentionTokensTest {
    @Test
    void convertsAllMentionTargetKindsToTokens() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        assertThat(MessageMentionTokens.token(new UserMentionTarget(userId))).isEqualTo(userId.toString());
        assertThat(MessageMentionTokens.token(new RoleMentionTarget(roleId))).isEqualTo(roleId.toString());
        assertThat(MessageMentionTokens.token(new ChannelMentionTarget(channelId))).isEqualTo(channelId.toString());
        for (SpecialMentionKind kind : SpecialMentionKind.values()) {
            assertThat(MessageMentionTokens.token(new SpecialMentionTarget(kind)))
                .isEqualTo(kind.name().toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void preservesInputOrderInAnUnmodifiableList() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<String> tokens = MessageMentionTokens.tokens(List.of(
            new RoleMentionTarget(roleId), new SpecialMentionTarget(SpecialMentionKind.HERE), new UserMentionTarget(userId)
        ));

        assertThat(tokens).containsExactly(roleId.toString(), "here", userId.toString());
        assertThatThrownBy(() -> tokens.add("another")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsAnEmptyListForNoMentions() {
        assertThat(MessageMentionTokens.tokens(List.of())).isEmpty();
    }
}
