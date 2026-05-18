package com.example.discord.storage;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class InMemoryAttachmentService {
    private final AttachmentUploadPolicy policy;
    private final ObjectStore objectStore;
    private final Clock clock;
    private final AttachmentScanner scanner;
    private final FileSignatureValidator signatureValidator = new FileSignatureValidator();
    private final Map<UUID, Attachment> attachments = new LinkedHashMap<>();

    public InMemoryAttachmentService(AttachmentUploadPolicy policy, ObjectStore objectStore, Clock clock) {
        this(policy, objectStore, clock, AttachmentScanner.allowAll());
    }

    public InMemoryAttachmentService(
        AttachmentUploadPolicy policy,
        ObjectStore objectStore,
        Clock clock,
        AttachmentScanner scanner
    ) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
    }

    public synchronized PresignedUpload requestUpload(AttachmentUploadRequest request) {
        requireRequest(request);
        String contentType = AttachmentUploadPolicy.normalizeContentType(request.contentType());
        policy.validate(contentType, request.sizeBytes());
        String filename = requireFilename(request.filename());
        policy.validateFilename(filename, contentType);
        UUID attachmentId = UUID.randomUUID();
        String objectKey = objectKey(request, attachmentId, extension(filename));
        Instant now = clock.instant();
        Attachment attachment = new Attachment(
            attachmentId,
            request.ownerId(),
            request.guildId(),
            request.channelId(),
            null,
            filename,
            contentType,
            request.sizeBytes(),
            objectKey,
            AttachmentStatus.PENDING,
            now,
            now
        );
        attachments.put(attachment.id(), attachment);
        return new PresignedUpload(attachment.id(), attachment.objectKey(), objectStore.presignUpload(attachment.objectKey()));
    }

    public synchronized Attachment markUploaded(UUID attachmentId, UUID ownerId) {
        Attachment current = requireOwnedAttachment(attachmentId, ownerId);
        return markUploaded(current);
    }

    public synchronized Attachment markUploaded(UUID attachmentId, UUID ownerId, byte[] bytes) {
        Attachment current = requireOwnedAttachment(attachmentId, ownerId);
        signatureValidator.validate(current.contentType(), bytes);
        AttachmentScanResult scanResult = scanner.scan(current, bytes.clone());
        if (scanResult.status() == AttachmentScanStatus.UNAVAILABLE) {
            throw new IllegalStateException("attachment scanner unavailable");
        }
        if (scanResult.status() == AttachmentScanStatus.BLOCKED) {
            throw new IllegalStateException("attachment blocked by scanner");
        }
        return markUploaded(current);
    }

    private Attachment markUploaded(Attachment current) {
        objectStore.put(current.objectKey());
        Attachment updated = new Attachment(
            current.id(),
            current.ownerId(),
            current.guildId(),
            current.channelId(),
            current.messageId(),
            current.filename(),
            current.contentType(),
            current.sizeBytes(),
            current.objectKey(),
            AttachmentStatus.UPLOADED,
            current.createdAt(),
            clock.instant()
        );
        attachments.put(updated.id(), updated);
        return updated;
    }

    public synchronized Attachment attachToMessage(
        UUID attachmentId,
        UUID ownerId,
        UUID guildId,
        UUID channelId,
        UUID messageId
    ) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Attachment current = requireScopedAttachment(attachmentId, ownerId, guildId, channelId);
        if (current.status() == AttachmentStatus.PENDING) {
            throw new IllegalStateException("attachment must be uploaded before attaching");
        }
        Attachment updated = new Attachment(
            current.id(),
            current.ownerId(),
            current.guildId(),
            current.channelId(),
            messageId,
            current.filename(),
            current.contentType(),
            current.sizeBytes(),
            current.objectKey(),
            AttachmentStatus.ATTACHED,
            current.createdAt(),
            clock.instant()
        );
        attachments.put(updated.id(), updated);
        return updated;
    }

    public synchronized PresignedDownload requestDownload(UUID attachmentId, UUID ownerId, UUID guildId, UUID channelId) {
        Attachment attachment = requireScopedAttachment(attachmentId, ownerId, guildId, channelId);
        if (attachment.status() != AttachmentStatus.ATTACHED) {
            throw new AttachmentNotFoundException();
        }
        return new PresignedDownload(attachment.id(), attachment.objectKey(), objectStore.presignDownload(attachment.objectKey()));
    }

    public synchronized Attachment attachment(UUID attachmentId, UUID ownerId, UUID guildId, UUID channelId) {
        return requireScopedAttachment(attachmentId, ownerId, guildId, channelId);
    }

    public synchronized Attachment attachmentByOwner(UUID attachmentId, UUID ownerId) {
        return requireOwnedAttachment(attachmentId, ownerId);
    }

    public synchronized int cleanupOrphans() {
        Instant cutoff = clock.instant().minus(policy.orphanTtl());
        int deleted = 0;
        var iterator = attachments.entrySet().iterator();
        while (iterator.hasNext()) {
            Attachment attachment = iterator.next().getValue();
            if (attachment.status() != AttachmentStatus.ATTACHED && !attachment.createdAt().isAfter(cutoff)) {
                objectStore.delete(attachment.objectKey());
                iterator.remove();
                deleted++;
            }
        }
        return deleted;
    }

    private Attachment requireOwnedAttachment(UUID attachmentId, UUID ownerId) {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Attachment attachment = attachments.get(attachmentId);
        if (attachment == null || !attachment.ownerId().equals(ownerId)) {
            throw new AttachmentNotFoundException();
        }
        return attachment;
    }

    private Attachment requireScopedAttachment(UUID attachmentId, UUID ownerId, UUID guildId, UUID channelId) {
        Attachment attachment = requireOwnedAttachment(attachmentId, ownerId);
        if (!attachment.guildId().equals(guildId) || !attachment.channelId().equals(channelId)) {
            throw new AttachmentNotFoundException();
        }
        return attachment;
    }

    private static void requireRequest(AttachmentUploadRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.ownerId(), "ownerId must not be null");
        Objects.requireNonNull(request.guildId(), "guildId must not be null");
        Objects.requireNonNull(request.channelId(), "channelId must not be null");
    }

    private static String requireFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("attachment filename is required");
        }
        return filename.trim();
    }

    private static String objectKey(AttachmentUploadRequest request, UUID attachmentId, String extension) {
        return "attachments/"
            + request.guildId()
            + "/"
            + request.channelId()
            + "/"
            + request.ownerId()
            + "/"
            + attachmentId
            + extension;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(java.util.Locale.ROOT);
        if (!extension.matches("\\.[a-z0-9]{1,12}")) {
            return "";
        }
        return extension;
    }
}
