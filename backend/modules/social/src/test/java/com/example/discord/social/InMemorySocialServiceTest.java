package com.example.discord.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemorySocialServiceTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DAVE = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void acceptsPendingFriendRequestByAddresseeOnly() {
        InMemorySocialService service = service();
        FriendRequest request = service.requestFriend(ALICE, BOB);

        assertThatThrownBy(() -> service.acceptFriendRequest(request.id(), ALICE))
            .isInstanceOf(SocialPolicyException.class);

        FriendRequest accepted = service.acceptFriendRequest(request.id(), BOB);

        assertThat(accepted.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(service.areFriends(ALICE, BOB)).isTrue();
    }

    @Test
    void rejectsDirectMessageWhenEitherUserBlockedTheOther() {
        InMemorySocialService service = service();

        service.blockUser(ALICE, BOB);

        assertThatThrownBy(() -> service.directMessageChannel(BOB, ALICE))
            .isInstanceOf(SocialPolicyException.class);
        assertThatThrownBy(() -> service.sendDirectMessage(ALICE, BOB, "blocked ping"))
            .isInstanceOf(SocialPolicyException.class);
    }

    @Test
    void allowsOnlyGroupOwnerToAddAndRemoveMembers() {
        InMemorySocialService service = service();
        GroupDmChannel group = service.createGroupDm(ALICE, "raid party", Set.of(BOB, CAROL));

        assertThatThrownBy(() -> service.addGroupMember(group.id(), BOB, DAVE))
            .isInstanceOf(SocialPolicyException.class);

        GroupDmChannel afterAdd = service.addGroupMember(group.id(), ALICE, DAVE);
        assertThat(afterAdd.members()).containsExactlyInAnyOrder(ALICE, BOB, CAROL, DAVE);

        assertThatThrownBy(() -> service.removeGroupMember(group.id(), CAROL, BOB))
            .isInstanceOf(SocialPolicyException.class);

        GroupDmChannel afterRemove = service.removeGroupMember(group.id(), ALICE, DAVE);
        assertThat(afterRemove.members()).containsExactlyInAnyOrder(ALICE, BOB, CAROL);
    }

    private static InMemorySocialService service() {
        AtomicInteger ticks = new AtomicInteger();
        Clock clock = new IncrementingClock(Instant.parse("2026-05-14T00:00:00Z"), ticks);
        return new InMemorySocialService(clock);
    }

    private static final class IncrementingClock extends Clock {
        private final Instant base;
        private final AtomicInteger ticks;

        private IncrementingClock(Instant base, AtomicInteger ticks) {
            this.base = base;
            this.ticks = ticks;
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
            return base.plusSeconds(ticks.getAndIncrement());
        }
    }
}
