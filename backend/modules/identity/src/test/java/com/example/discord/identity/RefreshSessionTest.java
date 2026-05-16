package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshSessionTest {
    @Test
    void rotationRevokesPreviousSessionAndCreatesNewSession() {
        Instant createdAt = Instant.parse("2026-05-13T00:00:00Z");
        RefreshSession session = RefreshSession.create(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "old-token-hash",
            "Chrome on Windows",
            createdAt,
            createdAt.plusSeconds(604800)
        );

        RefreshSession.Rotation rotation = session.rotate(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "new-token-hash",
            createdAt.plusSeconds(60),
            createdAt.plusSeconds(604860)
        );

        assertThat(rotation.revokedPrevious().revoked()).isTrue();
        assertThat(rotation.revokedPrevious().revokedAt()).contains(createdAt.plusSeconds(60));
        assertThat(rotation.next().id()).isEqualTo(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertThat(rotation.next().tokenHash()).isEqualTo("new-token-hash");
        assertThat(rotation.next().revoked()).isFalse();
    }

    @Test
    void rotateRejectsRevokedSession() {
        Instant createdAt = Instant.parse("2026-05-13T00:00:00Z");
        RefreshSession session = RefreshSession.create(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "old-token-hash",
            "Chrome on Windows",
            createdAt,
            createdAt.plusSeconds(604800)
        );
        RefreshSession revoked = session.rotate(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "new-token-hash",
            createdAt.plusSeconds(60),
            createdAt.plusSeconds(604860)
        ).revokedPrevious();

        assertThatThrownBy(() -> revoked.rotate(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "next-token-hash",
            createdAt.plusSeconds(120),
            createdAt.plusSeconds(604920)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("refresh session revoked");
    }

    @Test
    void rotateRejectsExpiredSession() {
        Instant createdAt = Instant.parse("2026-05-13T00:00:00Z");
        RefreshSession session = RefreshSession.create(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "old-token-hash",
            "Chrome on Windows",
            createdAt,
            createdAt.plusSeconds(60)
        );

        assertThatThrownBy(() -> session.rotate(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "new-token-hash",
            createdAt.plusSeconds(60),
            createdAt.plusSeconds(604860)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("refresh session expired");
    }

    @Test
    void revokeIsIdempotent() {
        Instant createdAt = Instant.parse("2026-05-13T00:00:00Z");
        Instant revokedAt = createdAt.plusSeconds(30);
        RefreshSession session = RefreshSession.create(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "old-token-hash",
            "Chrome on Windows",
            createdAt,
            createdAt.plusSeconds(604800)
        );

        RefreshSession revoked = session.revoke(revokedAt);

        assertThat(revoked.revoked()).isTrue();
        assertThat(revoked.revokedAt()).contains(revokedAt);
        assertThat(revoked.revoke(revokedAt.plusSeconds(10))).isSameAs(revoked);
    }
}
