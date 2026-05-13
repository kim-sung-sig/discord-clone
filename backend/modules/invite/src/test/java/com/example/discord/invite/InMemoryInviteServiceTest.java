package com.example.discord.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryInviteServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-13T00:00:00Z");

    @Test
    void rejectsExpiredInviteAccept() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryInviteService service = new InMemoryInviteService(clock);
        Invite invite = service.create(new CreateInviteCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            60,
            0,
            false,
            List.of()
        ));

        clock.advanceSeconds(61);

        assertThatThrownBy(() -> service.accept(invite.code(), UUID.randomUUID()))
            .isInstanceOf(InviteExpiredException.class);
    }

    @Test
    void enforcesMaxUsesUnderConcurrentAccepts() throws Exception {
        InMemoryInviteService service = new InMemoryInviteService(Clock.fixed(NOW, ZoneOffset.UTC));
        Invite invite = service.create(new CreateInviteCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            1,
            false,
            List.of()
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        service.accept(invite.code(), UUID.randomUUID());
                        accepted.incrementAndGet();
                    } catch (InviteMaxUsesExceededException ignored) {
                        // Expected for exactly one concurrent distinct accept.
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }

        assertThat(accepted).hasValue(1);
        assertThat(service.preview(invite.code()).uses()).isEqualTo(1);
    }

    @Test
    void sameMemberAcceptIsIdempotentAndDoesNotConsumeAdditionalUse() {
        InMemoryInviteService service = new InMemoryInviteService(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID memberId = UUID.randomUUID();
        Invite invite = service.create(new CreateInviteCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            1,
            false,
            List.of()
        ));

        InviteAcceptResult first = service.accept(invite.code(), memberId);
        InviteAcceptResult second = service.accept(invite.code(), memberId);

        assertThat(first.alreadyAccepted()).isFalse();
        assertThat(second.alreadyAccepted()).isTrue();
        assertThat(service.preview(invite.code()).uses()).isEqualTo(1);
    }

    @Test
    void rejectsDeletedInviteReuse() {
        InMemoryInviteService service = new InMemoryInviteService(Clock.fixed(NOW, ZoneOffset.UTC));
        Invite invite = service.create(new CreateInviteCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            0,
            false,
            List.of()
        ));

        service.delete(invite.code());

        assertThatThrownBy(() -> service.accept(invite.code(), UUID.randomUUID()))
            .isInstanceOf(InviteDeletedException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
