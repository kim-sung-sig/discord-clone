package com.example.discord.thread;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.channel.ChannelType;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
class ThreadController {
    private final InMemoryThreadService threadService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    ThreadController(
        InMemoryThreadService threadService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.threadService = threadService;
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/api/channels/{parentChannelId}/threads")
    ResponseEntity<ThreadResponse> createThread(
        @PathVariable UUID parentChannelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateThreadRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireSendMessages(parentChannelId, requesterId);
        requireRequest(request);
        ThreadChannel thread = threadService.createThread(new CreateThreadCommand(
            guildId,
            parentChannelId,
            requesterId,
            request.name(),
            request.type(),
            request.autoArchiveMinutesOrDefault()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ThreadResponse.from(thread));
    }

    @PostMapping("/api/channels/{forumChannelId}/forum-posts")
    ResponseEntity<ForumPostResponse> createForumPost(
        @PathVariable UUID forumChannelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateForumPostRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireSendMessages(forumChannelId, requesterId);
        requireForumChannel(guildId, forumChannelId);
        requireRequest(request);
        ForumPost post = threadService.createForumPost(new CreateForumPostCommand(
            guildId,
            forumChannelId,
            requesterId,
            request.title(),
            request.tagIds(),
            request.autoArchiveMinutesOrDefault()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ForumPostResponse.from(post));
    }

    @PostMapping("/api/channels/{forumChannelId}/forum-tags")
    ResponseEntity<ForumTagResponse> createForumTag(
        @PathVariable UUID forumChannelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateForumTagRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireSendMessages(forumChannelId, requesterId);
        requireForumChannel(guildId, forumChannelId);
        requireRequest(request);
        ForumTag tag = threadService.createForumTag(guildId, forumChannelId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ForumTagResponse.from(tag));
    }

    @PostMapping("/api/threads/{threadId}/messages")
    ResponseEntity<ThreadWriteResponse> write(
        @PathVariable UUID threadId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ThreadMessageRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        ThreadChannel thread = thread(threadId, requesterId);
        if (!guildService.canSendMessages(thread.guildId(), thread.parentChannelId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        requireRequest(request);
        ThreadWriteReceipt receipt = threadService.write(new ThreadWriteCommand(
            thread.guildId(),
            thread.id(),
            requesterId,
            request.content()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ThreadWriteResponse.from(receipt));
    }

    @PutMapping("/api/threads/{threadId}/archive")
    ThreadResponse archive(
        @PathVariable UUID threadId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        ThreadChannel thread = thread(threadId, requesterId);
        if (!guildService.canSendMessages(thread.guildId(), thread.parentChannelId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        return ThreadResponse.from(threadService.archive(thread.guildId(), thread.id()));
    }

    @PutMapping("/api/threads/{threadId}/reopen")
    ThreadResponse reopen(
        @PathVariable UUID threadId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        ThreadChannel thread = thread(threadId, requesterId);
        boolean canReopen = guildService.canSendMessages(thread.guildId(), thread.parentChannelId(), requesterId)
            || guildService.canManageMessages(thread.guildId(), thread.parentChannelId(), requesterId);
        if (!canReopen) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send or manage messages permission required");
        }
        return ThreadResponse.from(threadService.reopen(thread.guildId(), thread.id()));
    }

    @PostMapping("/api/threads/archive-expired")
    ArchiveExpiredResponse archiveExpired() {
        return new ArchiveExpiredResponse(threadService.archiveExpired());
    }

    private UUID requireSendMessages(UUID channelId, UUID requesterId) {
        UUID guildId = guildService.guildIdForChannel(channelId);
        if (!guildService.canSendMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        return guildId;
    }

    private void requireForumChannel(UUID guildId, UUID channelId) {
        if (guildService.channel(guildId, channelId).type() != ChannelType.GUILD_FORUM) {
            throw new IllegalArgumentException("forum channel required");
        }
    }

    private ThreadChannel thread(UUID threadId, UUID requesterId) {
        for (UUID guildId : guildService.guildIdsForMember(requesterId)) {
            try {
                return threadService.thread(guildId, threadId);
            } catch (ThreadNotFoundException ignored) {
                // Try the next guild the requester belongs to.
            }
        }
        throw new ThreadNotFoundException();
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record CreateThreadRequest(String name, ThreadType type, Integer autoArchiveMinutes) {
        int autoArchiveMinutesOrDefault() {
            return autoArchiveMinutes == null ? 1440 : autoArchiveMinutes;
        }
    }

    record CreateForumPostRequest(String title, List<UUID> tagIds, Integer autoArchiveMinutes) {
        int autoArchiveMinutesOrDefault() {
            return autoArchiveMinutes == null ? 1440 : autoArchiveMinutes;
        }
    }

    record CreateForumTagRequest(String name) {
    }

    record ThreadMessageRequest(String content) {
    }

    record ThreadResponse(
        UUID id,
        UUID guildId,
        UUID parentChannelId,
        UUID ownerId,
        String name,
        ThreadType type,
        boolean archived,
        int autoArchiveMinutes,
        Instant lastActivityAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        static ThreadResponse from(ThreadChannel thread) {
            return new ThreadResponse(
                thread.id(),
                thread.guildId(),
                thread.parentChannelId(),
                thread.ownerId(),
                thread.name(),
                thread.type(),
                thread.archived(),
                thread.autoArchiveMinutes(),
                thread.lastActivityAt(),
                thread.createdAt(),
                thread.updatedAt()
            );
        }
    }

    record ForumPostResponse(ThreadResponse thread, List<UUID> tagIds) {
        static ForumPostResponse from(ForumPost post) {
            return new ForumPostResponse(ThreadResponse.from(post.thread()), post.tagIds());
        }
    }

    record ForumTagResponse(UUID id, UUID guildId, UUID forumChannelId, String name) {
        static ForumTagResponse from(ForumTag tag) {
            return new ForumTagResponse(tag.id(), tag.guildId(), tag.forumChannelId(), tag.name());
        }
    }

    record ThreadWriteResponse(UUID threadId, UUID authorId, String content, Instant writtenAt) {
        static ThreadWriteResponse from(ThreadWriteReceipt receipt) {
            return new ThreadWriteResponse(receipt.threadId(), receipt.authorId(), receipt.content(), receipt.writtenAt());
        }
    }

    record ArchiveExpiredResponse(int archivedCount) {
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = ThreadController.class)
class ThreadControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ThreadController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ThreadController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ThreadController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ThreadController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(ThreadNotFoundException.class)
    ResponseEntity<ThreadController.ErrorResponse> missingThread(ThreadNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ThreadController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ThreadController.ErrorResponse> forbiddenState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ThreadController.ErrorResponse(exception.getMessage()));
    }
}
