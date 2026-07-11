package com.example.discord.invite;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.Guild;
import com.example.discord.guild.GuildMember;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.moderation.AuditLogAction;
import com.example.discord.moderation.InMemoryModerationService;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
class InviteController {
    private final InviteService inviteService;
    private final InMemoryGuildService guildService;
    private final InMemoryModerationService moderationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    InviteController(
        InviteService inviteService,
        InMemoryGuildService guildService,
        InMemoryModerationService moderationService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.inviteService = inviteService;
        this.guildService = guildService;
        this.moderationService = moderationService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/api/guilds/{guildId}/invites")
    ResponseEntity<InviteResponse> createInvite(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateInviteRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageChannels(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage channels permission required");
        }
        requireRequest(request);
        List<UUID> roleGrantIds = request.roleGrantIds() == null ? List.of() : List.copyOf(request.roleGrantIds());
        guildService.requireChannel(guildId, request.channelId());
        if (!guildService.canAssignRoles(guildId, requesterId, roleGrantIds)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot assign role above requester");
        }
        Invite invite = inviteService.create(new CreateInviteCommand(
            guildId,
            request.channelId(),
            requesterId,
            request.maxAgeSeconds(),
            request.maxUses(),
            request.temporary(),
            roleGrantIds
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(invite, guildService.guild(guildId)));
    }

    @GetMapping("/api/invites/{code}")
    InviteResponse previewInvite(@PathVariable String code) {
        Invite invite = inviteService.preview(code);
        return InviteResponse.from(invite, guildService.guild(invite.guildId()));
    }

    @PostMapping("/api/invites/{code}/accept")
    InviteAcceptResponse acceptInvite(
        @PathVariable String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Invite preview = inviteService.preview(code);
        guildService.requireRoles(preview.guildId(), preview.roleGrantIds());
        InviteAcceptResult result = inviteService.accept(code, requesterId);
        GuildMember member = guildService.addMember(result.invite().guildId(), requesterId);
        for (UUID roleGrantId : result.invite().roleGrantIds()) {
            member = guildService.assignRoleToMember(result.invite().guildId(), requesterId, roleGrantId);
        }
        return InviteAcceptResponse.from(result, member);
    }

    @DeleteMapping("/api/invites/{code}")
    ResponseEntity<Void> deleteInvite(
        @PathVariable String code,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Invite invite = inviteService.get(code);
        if (!guildService.canManageChannels(invite.guildId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage channels permission required");
        }
        inviteService.delete(code);
        moderationService.appendAudit(invite.guildId(), AuditLogAction.INVITE_DELETED, requesterId, invite.channelId(), "invite deleted: " + code);
        return ResponseEntity.noContent().build();
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record CreateInviteRequest(
        UUID channelId,
        long maxAgeSeconds,
        int maxUses,
        boolean temporary,
        List<UUID> roleGrantIds
    ) {
    }

    record InviteResponse(
        String code,
        UUID guildId,
        String guildName,
        UUID channelId,
        UUID creatorId,
        long maxAgeSeconds,
        int maxUses,
        boolean temporary,
        List<UUID> roleGrantIds,
        int uses,
        Instant expiresAt
    ) {
        static InviteResponse from(Invite invite, Guild guild) {
            return new InviteResponse(
                invite.code(),
                invite.guildId(),
                guild.name(),
                invite.channelId(),
                invite.creatorId(),
                invite.maxAgeSeconds(),
                invite.maxUses(),
                invite.temporary(),
                invite.roleGrantIds(),
                invite.uses(),
                invite.expiresAt()
            );
        }
    }

    record InviteAcceptResponse(
        UUID guildId,
        UUID memberId,
        List<UUID> roleIds,
        int uses,
        boolean alreadyAccepted
    ) {
        static InviteAcceptResponse from(InviteAcceptResult result, GuildMember member) {
            return new InviteAcceptResponse(
                result.invite().guildId(),
                result.memberId(),
                List.copyOf(member.roleIds()),
                result.invite().uses(),
                result.alreadyAccepted()
            );
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = InviteController.class)
class InviteControllerAdvice {
    @ExceptionHandler(InviteNotFoundException.class)
    ResponseEntity<InviteController.ErrorResponse> notFound(InviteNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new InviteController.ErrorResponse("invite not found"));
    }

    @ExceptionHandler(InviteDeletedException.class)
    ResponseEntity<InviteController.ErrorResponse> deleted(InviteDeletedException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(new InviteController.ErrorResponse("invite deleted"));
    }

    @ExceptionHandler(InviteExpiredException.class)
    ResponseEntity<InviteController.ErrorResponse> expired(InviteExpiredException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(new InviteController.ErrorResponse("invite expired"));
    }

    @ExceptionHandler(InviteMaxUsesExceededException.class)
    ResponseEntity<InviteController.ErrorResponse> maxUses(InviteMaxUsesExceededException exception) {
        return ResponseEntity.status(HttpStatus.GONE).body(new InviteController.ErrorResponse("invite max uses exceeded"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<InviteController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new InviteController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<InviteController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new InviteController.ErrorResponse("invalid request"));
    }
}
