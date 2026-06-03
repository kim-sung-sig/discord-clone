package com.example.discord.storage;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.MessageLookupPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AttachmentController {
    private final InMemoryAttachmentService attachmentService;
    private final InMemoryGuildService guildService;
    private final MessageLookupPort messageLookup;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    AttachmentController(
        InMemoryAttachmentService attachmentService,
        InMemoryGuildService guildService,
        MessageLookupPort messageLookup,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.attachmentService = attachmentService;
        this.guildService = guildService;
        this.messageLookup = messageLookup;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/attachments/uploads")
    ResponseEntity<UploadResponse> requestUpload(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody UploadRequest request
    ) {
        requireRequest(request);
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(request.channelId());
        if (!guildService.canSendMessages(guildId, request.channelId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        PresignedUpload upload = attachmentService.requestUpload(new AttachmentUploadRequest(
            requesterId,
            guildId,
            request.channelId(),
            request.filename(),
            request.contentType(),
            request.sizeBytes()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadResponse.from(upload));
    }

    @PutMapping("/attachments/{attachmentId}/uploaded")
    AttachmentResponse markUploaded(
        @PathVariable UUID attachmentId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return AttachmentResponse.from(attachmentService.markUploaded(attachmentId, requesterId));
    }

    @PostMapping("/channels/{channelId}/messages/{messageId}/attachments/{attachmentId}")
    AttachmentResponse attachToMessage(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @PathVariable UUID attachmentId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        if (!guildService.canSendMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        messageLookup.requireMessage(new ChannelMessageTarget(guildId, channelId), messageId);
        return AttachmentResponse.from(attachmentService.attachToMessage(
            attachmentId,
            requesterId,
            guildId,
            channelId,
            messageId
        ));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    DownloadResponse requestDownload(
        @PathVariable UUID attachmentId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Attachment attachment = attachmentService.attachmentByOwner(attachmentId, requesterId);
        if (!guildService.canViewChannel(attachment.guildId(), attachment.channelId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return DownloadResponse.from(attachmentService.requestDownload(
            attachmentId,
            requesterId,
            attachment.guildId(),
            attachment.channelId()
        ));
    }

    @DeleteMapping("/attachments/orphans")
    CleanupResponse cleanupOrphans(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return new CleanupResponse(attachmentService.cleanupOrphans(requesterId));
    }

    private UUID guildIdFor(UUID channelId) {
        return guildService.guildIdForChannel(channelId);
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record UploadRequest(UUID channelId, String filename, String contentType, long sizeBytes) {
    }

    record UploadResponse(UUID attachmentId, String objectKey, String uploadUrl) {
        static UploadResponse from(PresignedUpload upload) {
            return new UploadResponse(upload.attachmentId(), upload.objectKey(), upload.uploadUrl());
        }
    }

    record DownloadResponse(UUID attachmentId, String objectKey, String downloadUrl) {
        static DownloadResponse from(PresignedDownload download) {
            return new DownloadResponse(download.attachmentId(), download.objectKey(), download.downloadUrl());
        }
    }

    record AttachmentResponse(
        UUID id,
        UUID guildId,
        UUID channelId,
        UUID ownerId,
        UUID messageId,
        String filename,
        String contentType,
        long sizeBytes,
        String objectKey,
        AttachmentStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        static AttachmentResponse from(Attachment attachment) {
            return new AttachmentResponse(
                attachment.id(),
                attachment.guildId(),
                attachment.channelId(),
                attachment.ownerId(),
                attachment.messageId(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                attachment.objectKey(),
                attachment.status(),
                attachment.createdAt(),
                attachment.updatedAt()
            );
        }
    }

    record CleanupResponse(int deletedCount) {
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = AttachmentController.class)
class AttachmentControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AttachmentController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new AttachmentController.ErrorResponse("invalid attachment"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AttachmentController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new AttachmentController.ErrorResponse("invalid attachment"));
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ResponseEntity<AttachmentController.ErrorResponse> missingAttachment(AttachmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AttachmentController.ErrorResponse(exception.getMessage()));
    }
}
