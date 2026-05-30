package com.example.discord.guild;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.channel.ChannelType;
import com.example.discord.moderation.AuditLogAction;
import com.example.discord.moderation.InMemoryModerationService;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/guilds")
class GuildController {
    private final InMemoryGuildService guildService;
    private final InMemoryModerationService moderationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    GuildController(
        InMemoryGuildService guildService,
        InMemoryModerationService moderationService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.guildService = guildService;
        this.moderationService = moderationService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping
    ResponseEntity<GuildResponse> createGuild(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateGuildRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        Guild guild = guildService.createGuild(request.name(), requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(GuildResponse.from(guild));
    }

    @PostMapping("/{guildId}/channels")
    ResponseEntity<ChannelResponse> createChannel(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateChannelRequest request
    ) {
        requireManageChannels(guildId, authorization);
        requireRequest(request);
        if (request.type() == null) {
            throw new IllegalArgumentException("channel type is required");
        }
        Channel channel = guildService.createChannel(guildId, request.name(), request.type(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponse.from(channel));
    }

    @GetMapping("/{guildId}/channels/visible")
    List<ChannelResponse> visibleChannels(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = requireGuildMember(guildId, authorization);
        return guildService.visibleChannels(guildId, requesterId).stream()
            .map(ChannelResponse::from)
            .toList();
    }

    @GetMapping("/{guildId}/roles")
    List<RoleResponse> roles(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireGuildMember(guildId, authorization);
        return guildService.roles(guildId).stream()
            .map(RoleResponse::from)
            .toList();
    }

    @PostMapping("/{guildId}/roles")
    ResponseEntity<RoleResponse> createRole(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateRoleRequest request
    ) {
        UUID requesterId = requireManageRoles(guildId, authorization);
        requireRequest(request);
        PermissionSet permissions = permissionSet(request.permissions(), true);
        requireGrantableRolePermissions(guildId, requesterId, permissions);
        Role role = guildService.createRole(guildId, request.name(), permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(role));
    }

    @PutMapping("/{guildId}/roles/{roleId}/permissions")
    RoleResponse replaceRolePermissions(
        @PathVariable UUID guildId,
        @PathVariable UUID roleId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ReplaceRolePermissionsRequest request
    ) {
        UUID requesterId = requireManageRoles(guildId, authorization);
        requireRequest(request);
        PermissionSet permissions = permissionSet(request.permissions(), false);
        requireGrantableRolePermissions(guildId, requesterId, permissions);
        Role role = guildService.assignRolePermissions(guildId, roleId, permissions);
        return RoleResponse.from(role);
    }

    @PutMapping("/{guildId}/members/{memberId}")
    MemberRoleResponse addMember(
        @PathVariable UUID guildId,
        @PathVariable UUID memberId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireManageRoles(guildId, authorization);
        return MemberRoleResponse.from(guildService.addMember(guildId, memberId));
    }

    @PutMapping("/{guildId}/members/{memberId}/roles/{roleId}")
    MemberRoleResponse assignRoleToMember(
        @PathVariable UUID guildId,
        @PathVariable UUID memberId,
        @PathVariable UUID roleId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = requireManageRoles(guildId, authorization);
        requireAssignableRole(guildId, requesterId, roleId);
        GuildMember member = guildService.assignRoleToMember(guildId, memberId, roleId);
        moderationService.appendAudit(guildId, AuditLogAction.ROLE_ASSIGNED, requesterId, memberId, "role assigned");
        return MemberRoleResponse.from(member);
    }

    @PutMapping("/{guildId}/channels/{channelId}/overwrites/roles/{roleId}")
    ChannelResponse replaceChannelRoleOverwrite(
        @PathVariable UUID guildId,
        @PathVariable UUID channelId,
        @PathVariable UUID roleId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ReplaceChannelRoleOverwriteRequest request
    ) {
        requireManageChannels(guildId, authorization);
        requireRequest(request);
        Channel channel = guildService.addChannelRoleOverwrite(
            guildId,
            channelId,
            roleId,
            permissionSet(request.allow(), false),
            permissionSet(request.deny(), false)
        );
        return ChannelResponse.from(channel);
    }

    private UUID requireManageRoles(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageRoles(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage roles permission required");
        }
        return requesterId;
    }

    private void requireManageChannels(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageChannels(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage channels permission required");
        }
    }

    private UUID requireGuildMember(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.isGuildMemberOrOwner(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
        return requesterId;
    }

    private void requireGrantableRolePermissions(UUID guildId, UUID requesterId, PermissionSet permissions) {
        if (!guildService.canGrantRolePermissions(guildId, requesterId, permissions)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot grant permissions above requester");
        }
    }

    private void requireAssignableRole(UUID guildId, UUID requesterId, UUID roleId) {
        if (!guildService.canAssignRole(guildId, requesterId, roleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot assign role above requester");
        }
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    private static PermissionSet permissionSet(List<String> permissions, boolean allowMissing) {
        if (permissions == null) {
            if (allowMissing) {
                return PermissionSet.empty();
            }
            throw new IllegalArgumentException("permissions are required");
        }

        PermissionSet permissionSet = PermissionSet.empty();
        for (String permission : permissions) {
            if (permission == null) {
                throw new IllegalArgumentException("permission is required");
            }
            permissionSet = permissionSet.grant(Permission.valueOf(permission));
        }
        return permissionSet;
    }

    private static List<String> permissionNames(PermissionSet permissions) {
        return Arrays.stream(Permission.values())
            .filter(permission -> (permissions.raw() & permission.bit()) != 0)
            .map(Permission::name)
            .toList();
    }

    record CreateGuildRequest(String name, UUID ownerId) {
    }

    record CreateChannelRequest(String name, ChannelType type, UUID parentId) {
    }

    record CreateRoleRequest(String name, List<String> permissions) {
    }

    record ReplaceRolePermissionsRequest(List<String> permissions) {
    }

    record ReplaceChannelRoleOverwriteRequest(List<String> allow, List<String> deny) {
    }

    record GuildResponse(UUID id, String name, UUID ownerId) {
        static GuildResponse from(Guild guild) {
            return new GuildResponse(guild.id(), guild.name(), guild.ownerId());
        }
    }

    record ChannelResponse(UUID id, String name, ChannelType type, UUID parentId) {
        static ChannelResponse from(Channel channel) {
            return new ChannelResponse(channel.id(), channel.name(), channel.type(), channel.parentId());
        }
    }

    record RoleResponse(UUID id, String name, List<String> permissions) {
        static RoleResponse from(Role role) {
            return new RoleResponse(role.id(), role.name(), permissionNames(role.permissions()));
        }
    }

    record MemberRoleResponse(UUID memberId, List<UUID> roleIds) {
        static MemberRoleResponse from(GuildMember member) {
            return new MemberRoleResponse(member.userId(), List.copyOf(member.roleIds()));
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = GuildController.class)
class GuildControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<GuildController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new GuildController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<GuildController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new GuildController.ErrorResponse("invalid request"));
    }
}
