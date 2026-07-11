package com.example.discord.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresExpressionServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Autowired private DataSource dataSource;

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM message_reactions");
            statement.executeUpdate("DELETE FROM custom_emojis");
            statement.executeUpdate("DELETE FROM stickers");
        }
    }

    @Test
    void persistsReactionMembershipAndExpressionCatalogs() {
        JdbcExpressionService service = new JdbcExpressionService(dataSource);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", USER_ID);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", USER_ID);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", OTHER_USER_ID);

        assertThat(service.reactionSummaries(CHANNEL_ID, MESSAGE_ID)).singleElement().satisfies(summary -> {
            assertThat(summary.count()).isEqualTo(2);
            assertThat(summary.reactedBy(USER_ID)).isTrue();
        });

        service.removeReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", USER_ID);
        assertThat(service.reactionSummaries(CHANNEL_ID, MESSAGE_ID)).singleElement().satisfies(summary -> {
            assertThat(summary.count()).isEqualTo(1);
            assertThat(summary.reactedBy(USER_ID)).isFalse();
        });

        CustomEmoji emoji = service.createCustomEmoji(GUILD_ID, "shipit_2026", "emoji/shipit.png", USER_ID);
        Sticker sticker = service.createSticker(GUILD_ID, "approved", "approved sticker", USER_ID);
        assertThat(service.customEmojis(GUILD_ID)).extracting(CustomEmoji::id).containsExactly(emoji.id());
        assertThat(service.stickers(GUILD_ID)).extracting(Sticker::id).containsExactly(sticker.id());
        assertThatThrownBy(() -> service.createCustomEmoji(GUILD_ID, "bad name", "emoji/shipit.png", USER_ID))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("emoji name");
    }
}
