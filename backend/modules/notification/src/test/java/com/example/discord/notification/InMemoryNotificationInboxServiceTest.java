package com.example.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryNotificationInboxServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000431");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000432");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000433");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000434");
    private static final UUID VISIBLE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000435");
    private static final UUID HIDDEN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000436");
    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    private final InMemoryNotificationInboxService service =
        new InMemoryNotificationInboxService(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsMentionOnlyForVisibleMentionedRecipients() {
        service.recordMention(new MentionNotificationCommand(
            GUILD_ID,
            CHANNEL_ID,
            MESSAGE_ID,
            10L,
            AUTHOR_ID,
            Set.of(VISIBLE_USER_ID, HIDDEN_USER_ID, AUTHOR_ID),
            Set.of(VISIBLE_USER_ID),
            "mentioned you"
        ));

        assertThat(service.inbox(VISIBLE_USER_ID))
            .extracting(NotificationItem::kind)
            .containsExactly(NotificationKind.MENTION);
        assertThat(service.inbox(HIDDEN_USER_ID)).isEmpty();
        assertThat(service.inbox(AUTHOR_ID)).isEmpty();
        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(1L);
    }

    @Test
    void preferenceSuppressesNewMentionItems() {
        service.updatePreferences(VISIBLE_USER_ID, new NotificationPreferences(false, true, true));

        service.recordMention(new MentionNotificationCommand(
            GUILD_ID,
            CHANNEL_ID,
            MESSAGE_ID,
            10L,
            AUTHOR_ID,
            Set.of(VISIBLE_USER_ID),
            Set.of(VISIBLE_USER_ID),
            "mentioned you"
        ));

        assertThat(service.inbox(VISIBLE_USER_ID)).isEmpty();
    }

    @Test
    void dmAndServerNotificationsContributeToUnreadCountNewestFirst() {
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 11L, "new dm");
        service.recordServerNotification(VISIBLE_USER_ID, GUILD_ID, CHANNEL_ID, UUID.randomUUID(), 12L, "server event");

        List<NotificationItem> inbox = service.inbox(VISIBLE_USER_ID);

        assertThat(inbox).extracting(NotificationItem::kind)
            .containsExactly(NotificationKind.SERVER, NotificationKind.DM);
        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(2L);
    }

    @Test
    void markChannelReadClearsUnreadItemsUpToSequence() {
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 11L, "old dm");
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 12L, "new dm");

        service.markChannelRead(VISIBLE_USER_ID, CHANNEL_ID, 11L);

        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(1L);
        assertThat(service.inbox(VISIBLE_USER_ID))
            .filteredOn(NotificationItem::read)
            .extracting(NotificationItem::sequence)
            .containsExactly(11L);
    }
}
