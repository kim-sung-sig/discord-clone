package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageMentionTargetTest {
    @Test
    void rejectsUserMentionWithoutUserId() {
        assertThatThrownBy(() -> new UserMentionTarget(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("userId must not be null");
    }

    @Test
    void rejectsRoleMentionWithoutRoleId() {
        assertThatThrownBy(() -> new RoleMentionTarget(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("roleId must not be null");
    }

    @Test
    void rejectsChannelMentionWithoutChannelId() {
        assertThatThrownBy(() -> new ChannelMentionTarget(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("channelId must not be null");
    }

    @Test
    void rejectsSpecialMentionWithoutKind() {
        assertThatThrownBy(() -> new SpecialMentionTarget(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("kind must not be null");
    }
}
