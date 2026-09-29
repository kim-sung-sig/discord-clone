package com.example.discord.social;

import com.example.discord.auth.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/social")
class SocialController {
    private final InMemorySocialService socialService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final JdbcDirectMessageService jdbcDirectMessageService;

    SocialController(
        InMemorySocialService socialService,
        AuthenticatedUserResolver authenticatedUserResolver,
        ObjectProvider<JdbcDirectMessageService> jdbcDirectMessageService
    ) {
        this.socialService = socialService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.jdbcDirectMessageService = jdbcDirectMessageService.getIfAvailable();
    }

    @PostMapping("/friends/requests")
    ResponseEntity<FriendRequestResponse> requestFriend(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody FriendTargetRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        FriendRequest friendRequest = socialService.requestFriend(requesterId, request.targetUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(FriendRequestResponse.from(friendRequest));
    }

    @PutMapping("/friends/requests/{requestId}/accept")
    FriendRequestResponse acceptFriendRequest(
        @PathVariable UUID requestId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return FriendRequestResponse.from(socialService.acceptFriendRequest(requestId, requesterId));
    }

    @PutMapping("/friends/requests/{requestId}/decline")
    FriendRequestResponse declineFriendRequest(
        @PathVariable UUID requestId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return FriendRequestResponse.from(socialService.declineFriendRequest(requestId, requesterId));
    }

    @PutMapping("/blocks/{targetUserId}")
    ResponseEntity<Void> block(
        @PathVariable UUID targetUserId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (jdbcDirectMessageService == null) {
            socialService.blockUser(requesterId, targetUserId);
        } else {
            jdbcDirectMessageService.block(requesterId, targetUserId);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dms")
    ResponseEntity<DirectMessageChannelResponse> directMessageChannel(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody FriendTargetRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        DirectMessageChannel channel = jdbcDirectMessageService == null
            ? socialService.directMessageChannel(requesterId, request.targetUserId())
            : jdbcDirectMessageService.channel(requesterId, request.targetUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(DirectMessageChannelResponse.from(channel));
    }

    @PostMapping("/dms/{targetUserId}/messages")
    ResponseEntity<DirectMessageReceiptResponse> sendDirectMessage(
        @PathVariable UUID targetUserId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody MessageRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        DirectMessageReceipt receipt = jdbcDirectMessageService == null
            ? socialService.sendDirectMessage(requesterId, targetUserId, request.content())
            : jdbcDirectMessageService.send(requesterId, targetUserId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(DirectMessageReceiptResponse.from(receipt));
    }

    @GetMapping("/dms/{targetUserId}/messages")
    List<JdbcDirectMessageService.DirectMessage> directMessages(
        @PathVariable UUID targetUserId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int limit
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (jdbcDirectMessageService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "direct message history requires persistence");
        }
        return jdbcDirectMessageService.messages(requesterId, targetUserId, beforeSequence, limit);
    }

    @PutMapping("/dms/{targetUserId}/read")
    DmReadResponse markDirectMessageRead(
        @PathVariable UUID targetUserId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody DmReadRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        if (jdbcDirectMessageService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "direct message read markers require persistence");
        }
        return new DmReadResponse(jdbcDirectMessageService.markRead(requesterId, targetUserId, request.lastReadSequence()));
    }

    @GetMapping("/dms/{targetUserId}/read")
    DmReadResponse directMessageReadMarker(
        @PathVariable UUID targetUserId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (jdbcDirectMessageService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "direct message read markers require persistence");
        }
        return new DmReadResponse(jdbcDirectMessageService.readSequence(requesterId, targetUserId));
    }

    @PostMapping("/group-dms")
    ResponseEntity<GroupDmResponse> createGroupDm(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateGroupDmRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        GroupDmChannel group = socialService.createGroupDm(requesterId, request.name(), request.memberIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupDmResponse.from(group));
    }

    @PutMapping("/group-dms/{groupId}/members/{memberId}")
    GroupDmResponse addGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID memberId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return GroupDmResponse.from(socialService.addGroupMember(groupId, requesterId, memberId));
    }

    @DeleteMapping("/group-dms/{groupId}/members/{memberId}")
    GroupDmResponse removeGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID memberId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return GroupDmResponse.from(socialService.removeGroupMember(groupId, requesterId, memberId));
    }

    @PutMapping("/group-dms/{groupId}/call")
    GroupDmResponse setGroupCallState(
        @PathVariable UUID groupId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody GroupCallRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        return GroupDmResponse.from(socialService.setGroupCallState(groupId, requesterId, request.active()));
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record FriendTargetRequest(UUID targetUserId) {
    }

    record MessageRequest(@NotBlank @Size(max = 4000) String content) {
    }

    record DmReadRequest(long lastReadSequence) {
    }

    record DmReadResponse(long lastReadSequence) {
    }

    record CreateGroupDmRequest(String name, Set<UUID> memberIds) {
    }

    record GroupCallRequest(boolean active) {
    }

    record FriendRequestResponse(
        UUID id,
        UUID requesterId,
        UUID addresseeId,
        FriendshipStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        static FriendRequestResponse from(FriendRequest request) {
            return new FriendRequestResponse(
                request.id(),
                request.requesterId(),
                request.addresseeId(),
                request.status(),
                request.createdAt(),
                request.updatedAt()
            );
        }
    }

    record DirectMessageChannelResponse(
        UUID id,
        UUID firstUserId,
        UUID secondUserId,
        Set<UUID> participants,
        Instant createdAt
    ) {
        static DirectMessageChannelResponse from(DirectMessageChannel channel) {
            return new DirectMessageChannelResponse(
                channel.id(),
                channel.firstUserId(),
                channel.secondUserId(),
                channel.participants(),
                channel.createdAt()
            );
        }
    }

    record DirectMessageReceiptResponse(
        UUID id,
        UUID channelId,
        UUID senderId,
        UUID targetUserId,
        String content,
        Instant acceptedAt
    ) {
        static DirectMessageReceiptResponse from(DirectMessageReceipt receipt) {
            return new DirectMessageReceiptResponse(
                receipt.id(),
                receipt.channelId(),
                receipt.senderId(),
                receipt.targetUserId(),
                receipt.content(),
                receipt.acceptedAt()
            );
        }
    }

    record GroupDmResponse(
        UUID id,
        String name,
        UUID ownerId,
        Set<UUID> members,
        GroupCallResponse callState,
        Instant createdAt,
        Instant updatedAt
    ) {
        static GroupDmResponse from(GroupDmChannel group) {
            return new GroupDmResponse(
                group.id(),
                group.name(),
                group.ownerId(),
                group.members(),
                GroupCallResponse.from(group.callState()),
                group.createdAt(),
                group.updatedAt()
            );
        }
    }

    record GroupCallResponse(boolean active, Set<UUID> participants) {
        static GroupCallResponse from(GroupCallState callState) {
            return new GroupCallResponse(callState.active(), callState.participants());
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = SocialController.class)
class SocialControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<SocialController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new SocialController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<SocialController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new SocialController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(SocialPolicyException.class)
    ResponseEntity<SocialController.ErrorResponse> socialPolicy(SocialPolicyException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new SocialController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(SocialNotFoundException.class)
    ResponseEntity<SocialController.ErrorResponse> notFound(SocialNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new SocialController.ErrorResponse(exception.getMessage()));
    }
}
