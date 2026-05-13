package com.example.discord.social;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class InMemorySocialService {
    private final Clock clock;
    private final Map<UUID, FriendRequest> friendRequests = new HashMap<>();
    private final Map<UserPair, FriendshipStatus> relationships = new HashMap<>();
    private final Set<BlockPair> blocks = new HashSet<>();
    private final Map<UUID, DirectMessageChannel> directMessages = new HashMap<>();
    private final Map<UUID, GroupDmChannel> groupDms = new HashMap<>();

    public InMemorySocialService() {
        this(Clock.systemUTC());
    }

    public InMemorySocialService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized FriendRequest requestFriend(UUID requesterId, UUID addresseeId) {
        requireDifferentUsers(requesterId, addresseeId);
        if (isBlockedEitherWay(requesterId, addresseeId)) {
            throw new SocialPolicyException("blocked users cannot create friend requests");
        }
        UserPair pair = UserPair.of(requesterId, addresseeId);
        FriendshipStatus existing = relationships.get(pair);
        if (existing == FriendshipStatus.ACCEPTED || existing == FriendshipStatus.PENDING) {
            throw new SocialPolicyException("friend request already exists");
        }
        Instant now = clock.instant();
        FriendRequest request = new FriendRequest(UUID.randomUUID(), requesterId, addresseeId, FriendshipStatus.PENDING, now, now);
        friendRequests.put(request.id(), request);
        relationships.put(pair, FriendshipStatus.PENDING);
        return request;
    }

    public synchronized FriendRequest acceptFriendRequest(UUID requestId, UUID requesterId) {
        FriendRequest request = requireFriendRequest(requestId);
        if (!request.addresseeId().equals(requesterId)) {
            throw new SocialPolicyException("only the addressee can accept a friend request");
        }
        if (request.status() != FriendshipStatus.PENDING) {
            throw new SocialPolicyException("only pending friend requests can be accepted");
        }
        if (isBlockedEitherWay(request.requesterId(), request.addresseeId())) {
            throw new SocialPolicyException("blocked users cannot become friends");
        }
        FriendRequest accepted = updateRequest(request, FriendshipStatus.ACCEPTED);
        relationships.put(UserPair.of(request.requesterId(), request.addresseeId()), FriendshipStatus.ACCEPTED);
        return accepted;
    }

    public synchronized FriendRequest declineFriendRequest(UUID requestId, UUID requesterId) {
        FriendRequest request = requireFriendRequest(requestId);
        if (!request.addresseeId().equals(requesterId) && !request.requesterId().equals(requesterId)) {
            throw new SocialPolicyException("only participants can decline a friend request");
        }
        if (request.status() != FriendshipStatus.PENDING) {
            throw new SocialPolicyException("only pending friend requests can be declined");
        }
        FriendRequest declined = updateRequest(request, FriendshipStatus.DECLINED);
        relationships.put(UserPair.of(request.requesterId(), request.addresseeId()), FriendshipStatus.DECLINED);
        return declined;
    }

    public synchronized void blockUser(UUID blockerId, UUID blockedId) {
        requireDifferentUsers(blockerId, blockedId);
        blocks.add(new BlockPair(blockerId, blockedId));
        relationships.put(UserPair.of(blockerId, blockedId), FriendshipStatus.BLOCKED);
        friendRequests.replaceAll((id, request) -> {
            if (UserPair.of(request.requesterId(), request.addresseeId()).equals(UserPair.of(blockerId, blockedId))
                && request.status() == FriendshipStatus.PENDING) {
                return updateRequest(request, FriendshipStatus.BLOCKED);
            }
            return request;
        });
    }

    public synchronized boolean areFriends(UUID firstUserId, UUID secondUserId) {
        return relationships.get(UserPair.of(firstUserId, secondUserId)) == FriendshipStatus.ACCEPTED;
    }

    public synchronized DirectMessageChannel directMessageChannel(UUID requesterId, UUID targetUserId) {
        requireCanDm(requesterId, targetUserId);
        UserPair pair = UserPair.of(requesterId, targetUserId);
        UUID channelId = pair.deterministicId("dm");
        return directMessages.computeIfAbsent(channelId, id ->
            new DirectMessageChannel(id, pair.firstUserId(), pair.secondUserId(), clock.instant())
        );
    }

    public synchronized DirectMessageReceipt sendDirectMessage(UUID senderId, UUID targetUserId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("message content is required");
        }
        DirectMessageChannel channel = directMessageChannel(senderId, targetUserId);
        return new DirectMessageReceipt(UUID.randomUUID(), channel.id(), senderId, targetUserId, content, clock.instant());
    }

    public synchronized GroupDmChannel createGroupDm(UUID ownerId, String name, Set<UUID> memberIds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("group name is required");
        }
        Set<UUID> members = new LinkedHashSet<>(memberIds == null ? Set.of() : memberIds);
        members.add(ownerId);
        Instant now = clock.instant();
        GroupDmChannel group = new GroupDmChannel(
            UUID.randomUUID(),
            name,
            ownerId,
            members,
            new GroupCallState(false, Set.of()),
            now,
            now
        );
        groupDms.put(group.id(), group);
        return group;
    }

    public synchronized GroupDmChannel addGroupMember(UUID groupId, UUID requesterId, UUID memberId) {
        GroupDmChannel group = requireGroup(groupId);
        requireGroupOwner(group, requesterId);
        Set<UUID> members = new LinkedHashSet<>(group.members());
        members.add(memberId);
        return saveGroup(group, members, group.callState());
    }

    public synchronized GroupDmChannel removeGroupMember(UUID groupId, UUID requesterId, UUID memberId) {
        GroupDmChannel group = requireGroup(groupId);
        requireGroupOwner(group, requesterId);
        if (group.ownerId().equals(memberId)) {
            throw new SocialPolicyException("group owner cannot be removed");
        }
        Set<UUID> members = new LinkedHashSet<>(group.members());
        members.remove(memberId);
        return saveGroup(group, members, group.callState());
    }

    public synchronized GroupDmChannel setGroupCallState(UUID groupId, UUID requesterId, boolean active) {
        GroupDmChannel group = requireGroup(groupId);
        if (!group.members().contains(requesterId)) {
            throw new SocialPolicyException("group member required");
        }
        GroupCallState callState = active
            ? new GroupCallState(true, group.members())
            : new GroupCallState(false, Set.of());
        return saveGroup(group, group.members(), callState);
    }

    private void requireCanDm(UUID requesterId, UUID targetUserId) {
        requireDifferentUsers(requesterId, targetUserId);
        if (isBlockedEitherWay(requesterId, targetUserId)) {
            throw new SocialPolicyException("blocked users cannot use direct messages");
        }
    }

    private boolean isBlockedEitherWay(UUID firstUserId, UUID secondUserId) {
        return blocks.contains(new BlockPair(firstUserId, secondUserId))
            || blocks.contains(new BlockPair(secondUserId, firstUserId));
    }

    private FriendRequest requireFriendRequest(UUID requestId) {
        FriendRequest request = friendRequests.get(requestId);
        if (request == null) {
            throw new SocialNotFoundException("friend request not found");
        }
        return request;
    }

    private GroupDmChannel requireGroup(UUID groupId) {
        GroupDmChannel group = groupDms.get(groupId);
        if (group == null) {
            throw new SocialNotFoundException("group dm not found");
        }
        return group;
    }

    private void requireGroupOwner(GroupDmChannel group, UUID requesterId) {
        if (!group.ownerId().equals(requesterId)) {
            throw new SocialPolicyException("group dm owner required");
        }
    }

    private FriendRequest updateRequest(FriendRequest request, FriendshipStatus status) {
        FriendRequest updated = new FriendRequest(
            request.id(),
            request.requesterId(),
            request.addresseeId(),
            status,
            request.createdAt(),
            clock.instant()
        );
        friendRequests.put(updated.id(), updated);
        return updated;
    }

    private GroupDmChannel saveGroup(GroupDmChannel group, Set<UUID> members, GroupCallState callState) {
        GroupDmChannel updated = new GroupDmChannel(
            group.id(),
            group.name(),
            group.ownerId(),
            members,
            callState,
            group.createdAt(),
            clock.instant()
        );
        groupDms.put(updated.id(), updated);
        return updated;
    }

    private static void requireDifferentUsers(UUID firstUserId, UUID secondUserId) {
        if (firstUserId == null || secondUserId == null || firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException("two different users are required");
        }
    }

    private record UserPair(UUID firstUserId, UUID secondUserId) {
        private static UserPair of(UUID left, UUID right) {
            return left.compareTo(right) <= 0 ? new UserPair(left, right) : new UserPair(right, left);
        }

        private UUID deterministicId(String prefix) {
            return UUID.nameUUIDFromBytes((prefix + ":" + firstUserId + ":" + secondUserId).getBytes(StandardCharsets.UTF_8));
        }
    }

    private record BlockPair(UUID blockerId, UUID blockedId) {
    }
}
