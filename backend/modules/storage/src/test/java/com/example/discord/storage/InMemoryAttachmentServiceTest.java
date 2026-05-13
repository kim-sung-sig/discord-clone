package com.example.discord.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryAttachmentServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-14T00:00:00Z"));
    private final InMemoryObjectStore objectStore = new InMemoryObjectStore();
    private final InMemoryAttachmentService service = new InMemoryAttachmentService(
        new AttachmentUploadPolicy(1024, java.util.Set.of("image/png", "image/jpeg"), Duration.ofMinutes(5)),
        objectStore,
        clock
    );

    @Test
    void rejectsOversizedUploads() {
        assertThatThrownBy(() -> service.requestUpload(
                new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "large.png", "image/png", 1025)
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("attachment size exceeds policy");
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> service.requestUpload(
                new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "notes.txt", "text/plain", 100)
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("attachment content type is not allowed");
    }

    @Test
    void generatesObjectKeyFromServerScopeAndNeverFromFilename() {
        PresignedUpload upload = service.requestUpload(
            new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "../../secret.png", "image/png", 100)
        );

        assertThat(upload.objectKey()).startsWith("attachments/" + GUILD_ID + "/" + CHANNEL_ID + "/" + OWNER_ID + "/");
        assertThat(upload.objectKey()).endsWith(".png");
        assertThat(upload.objectKey()).doesNotContain("..");
        assertThat(upload.objectKey()).doesNotContain("secret");
        assertThat(upload.uploadUrl()).isEqualTo("memory://upload/" + upload.objectKey());
    }

    @Test
    void issuesDownloadOnlyForOwnerAndChannelScope() {
        PresignedUpload upload = service.requestUpload(
            new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "image.png", "image/png", 100)
        );
        service.markUploaded(upload.attachmentId(), OWNER_ID);
        service.attachToMessage(upload.attachmentId(), OWNER_ID, GUILD_ID, CHANNEL_ID, MESSAGE_ID);

        PresignedDownload download = service.requestDownload(upload.attachmentId(), OWNER_ID, GUILD_ID, CHANNEL_ID);

        assertThat(download.downloadUrl()).isEqualTo("memory://download/" + upload.objectKey());
        assertThatThrownBy(() -> service.requestDownload(upload.attachmentId(), OTHER_OWNER_ID, GUILD_ID, CHANNEL_ID))
            .isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> service.requestDownload(upload.attachmentId(), OWNER_ID, GUILD_ID, OTHER_CHANNEL_ID))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void cleanupDeletesPendingOrphansAfterTtlButKeepsAttachedUploads() {
        PresignedUpload orphan = service.requestUpload(
            new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "orphan.png", "image/png", 100)
        );
        PresignedUpload attached = service.requestUpload(
            new AttachmentUploadRequest(OWNER_ID, GUILD_ID, CHANNEL_ID, "attached.jpg", "image/jpeg", 100)
        );
        service.markUploaded(orphan.attachmentId(), OWNER_ID);
        service.markUploaded(attached.attachmentId(), OWNER_ID);
        service.attachToMessage(attached.attachmentId(), OWNER_ID, GUILD_ID, CHANNEL_ID, MESSAGE_ID);
        objectStore.put(orphan.objectKey());
        objectStore.put(attached.objectKey());

        clock.advance(Duration.ofMinutes(6));

        int deleted = service.cleanupOrphans();

        assertThat(deleted).isEqualTo(1);
        assertThat(objectStore.exists(orphan.objectKey())).isFalse();
        assertThat(objectStore.exists(attached.objectKey())).isTrue();
        assertThat(service.attachment(attached.attachmentId(), OWNER_ID, GUILD_ID, CHANNEL_ID).status())
            .isEqualTo(AttachmentStatus.ATTACHED);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
