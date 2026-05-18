package com.example.discord.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryServerEventServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STAGE_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID HIDDEN_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CREATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void createsServerEventAndMemberCanRsvpOnce() {
        InMemoryServerEventService service = new InMemoryServerEventService();
        ServerEvent event = service.createEvent(command(STAGE_CHANNEL_ID), true);

        ServerEvent rsvp = service.rsvpInterested(GUILD_ID, event.id(), MEMBER_ID);
        ServerEvent duplicate = service.rsvpInterested(GUILD_ID, event.id(), MEMBER_ID);

        assertThat(rsvp.interestedMemberIds()).containsExactly(MEMBER_ID);
        assertThat(duplicate.interestedMemberIds()).containsExactly(MEMBER_ID);
        assertThat(service.signals()).extracting(ServerEventSignal::type)
            .containsExactly(ServerEventSignalType.EVENT_RSVP_UPDATED, ServerEventSignalType.EVENT_CREATED);
    }

    @Test
    void visibleEventsExcludeHiddenChannelEvents() {
        InMemoryServerEventService service = new InMemoryServerEventService();
        ServerEvent visible = service.createEvent(command(STAGE_CHANNEL_ID), true);
        service.createEvent(command(HIDDEN_CHANNEL_ID), true);

        assertThat(service.visibleEvents(GUILD_ID, Set.of(STAGE_CHANNEL_ID)))
            .extracting(ServerEvent::id)
            .containsExactly(visible.id());
    }

    @Test
    void cancelEventRecordsSignalAndTerminalState() {
        InMemoryServerEventService service = new InMemoryServerEventService();
        ServerEvent event = service.createEvent(command(STAGE_CHANNEL_ID), true);

        ServerEvent canceled = service.cancelEvent(GUILD_ID, event.id(), CREATOR_ID, "speaker unavailable");

        assertThat(canceled.status()).isEqualTo(ServerEventStatus.CANCELED);
        assertThat(service.signals()).extracting(ServerEventSignal::type)
            .containsExactly(ServerEventSignalType.EVENT_CANCELED, ServerEventSignalType.EVENT_CREATED);
    }

    @Test
    void rejectsCreateWithoutPermissionOrInvalidTimeRange() {
        InMemoryServerEventService service = new InMemoryServerEventService();

        assertThatThrownBy(() -> service.createEvent(command(STAGE_CHANNEL_ID), false))
            .isInstanceOf(SecurityException.class)
            .hasMessage("server event management permission is required");
        assertThatThrownBy(() -> service.createEvent(new CreateServerEventCommand(
            GUILD_ID,
            STAGE_CHANNEL_ID,
            CREATOR_ID,
            "bad time",
            Instant.parse("2026-05-18T11:00:00Z"),
            Instant.parse("2026-05-18T10:00:00Z")
        ), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event end must be after start");
    }

    private static CreateServerEventCommand command(UUID channelId) {
        return new CreateServerEventCommand(
            GUILD_ID,
            channelId,
            CREATOR_ID,
            "Weekly stage",
            Instant.parse("2026-05-18T10:00:00Z"),
            Instant.parse("2026-05-18T11:00:00Z")
        );
    }
}
