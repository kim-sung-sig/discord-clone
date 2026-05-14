import { defineStore } from 'pinia'
import {
  normalizeGatewayDispatch,
  toShellGatewayEvent,
  type GatewayDispatch
} from '../services/gateway-client'

export type ShellChannelType = 'GUILD_TEXT' | 'GUILD_VOICE' | 'GUILD_FORUM'

export interface ShellGuild {
  id: string
  name: string
}

export interface ShellChannel {
  id: string
  name: string
  type: ShellChannelType
}

export interface ShellMessage {
  id: string
  channelId: string
  sequence: number
  author: string
  body: string
  createdAt: string
  edited: boolean
  pinned: boolean
  deleted: boolean
  mentions: string[]
  attachments: ShellAttachment[]
}

export interface ShellAttachment {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  previewUrl: string
}

export type ShellExpressionType = 'EMOJI' | 'STICKER'

export interface ShellExpression {
  key: string
  label: string
  type: ShellExpressionType
  symbol: string
  description: string
}

export interface ShellReaction {
  messageId: string
  emojiKey: string
  reactorIds: string[]
}

export interface ShellReactionSummary {
  messageId: string
  emojiKey: string
  label: string
  symbol: string
  count: number
  reactedByCurrentUser: boolean
}

export interface ShellChannelGroup {
  id: string
  name: string
  channels: ShellChannel[]
}

export interface ShellRole {
  id: string
  name: string
  permissions: string[]
}

export interface ShellPermissionOverwrite {
  channelId: string
  roleId: string
  allow: string[]
  deny: string[]
}

export interface ShellInvitePreview {
  code: string
  guildId: string
  channelId: string
  expiresIn: string
  maxUses: number
  uses: number
  roleGrantIds: string[]
}

export interface ShellMember {
  name: string
  status: string
  roleIds: string[]
}

export type GatewayConnectionStatus = 'READY' | 'DISCONNECTED'

export interface ShellGatewayEvent {
  sequence: number
  type: string
  label: string
}

export interface ShellGatewayState {
  status: GatewayConnectionStatus
  sessionId: string
  lastSequence: number
  lastHeartbeatAckAt: string
  resumed: boolean
  events: ShellGatewayEvent[]
}

export type ShellFriendshipStatus = 'FRIEND' | 'BLOCKED'
export type ShellSocialSelectionType = 'DIRECT' | 'GROUP'

export interface ShellFriend {
  id: string
  name: string
  status: ShellFriendshipStatus
  directMessageId: string
}

export interface ShellDirectMessage {
  id: string
  userId: string
  unreadCount: number
}

export interface ShellGroupCall {
  active: boolean
  participants: string[]
}

export interface ShellGroupDm {
  id: string
  name: string
  ownerId: string
  memberIds: string[]
  call: ShellGroupCall
}

export interface ShellSocialState {
  activeSelection: {
    type: ShellSocialSelectionType
    id: string
  }
  friends: ShellFriend[]
  directMessages: ShellDirectMessage[]
  groupDms: ShellGroupDm[]
}

export type ShellThreadType = 'PUBLIC' | 'PRIVATE'

export interface ShellForumTag {
  id: string
  label: string
  required: boolean
}

export interface ShellForum {
  channelId: string
  guidelines: string
  layout: 'LIST' | 'GALLERY'
  tags: ShellForumTag[]
}

export interface ShellThread {
  id: string
  parentChannelId: string
  title: string
  type: ShellThreadType
  tagIds: string[]
  archived: boolean
  autoArchiveAt: string
  writeReceipt: string
}

export interface ShellForumState {
  activeForumChannelId: string
  activeThreadId: string
  forums: ShellForum[]
  threads: ShellThread[]
  postError: string
}

export interface ShellOnboardingAnswer {
  id: string
  label: string
  roleId: string
  roleName: string
}

export interface ShellOnboardingQuestion {
  id: string
  prompt: string
  answers: ShellOnboardingAnswer[]
}

export interface ShellAutoModRule {
  id: string
  keyword: string
  enabled: boolean
}

export interface ShellAuditLogEntry {
  id: string
  action: string
  detail: string
  createdAt: string
}

export interface ShellModerationState {
  onboardingQuestion: ShellOnboardingQuestion
  selectedAnswerId: string | null
  assignedRoleName: string
  automodRules: ShellAutoModRule[]
  decision: string
  auditLogs: ShellAuditLogEntry[]
}

export interface ShellVoiceParticipant {
  userId: string
  channelId: string
  muted: boolean
  deafened: boolean
  speaking: boolean
  screenSharing: boolean
}

export interface ShellVoiceEvent {
  id: string
  type: string
  detail: string
}

export interface ShellVoiceState {
  activeChannelId: string | null
  tokenProvider: 'LIVEKIT_SKELETON'
  token: string | null
  participants: ShellVoiceParticipant[]
  events: ShellVoiceEvent[]
}

export type ShellPresenceStatus = 'ONLINE' | 'IDLE' | 'DO_NOT_DISTURB' | 'OFFLINE'

export interface ShellPresenceRecord {
  userId: string
  status: ShellPresenceStatus
  expiresAt: number | null
}

export interface ShellTypingRecord {
  userId: string
  expiresAt: number
}

export interface ShellReadMarker {
  channelId: string
  lastReadSequence: number
}

export interface ShellPresenceState {
  nowMs: number
  users: Record<string, ShellPresenceRecord>
  typingByChannel: Record<string, ShellTypingRecord[]>
  readMarkers: Record<string, ShellReadMarker>
}

const extractMentions = (body: string): string[] => {
  const mentions = new Set<string>()
  const mentionPattern = /(?<![A-Za-z0-9_.<])@([A-Za-z0-9][A-Za-z0-9-]{0,31})/g
  for (const match of body.matchAll(mentionPattern)) {
    mentions.add(match[1].toLowerCase())
  }
  return Array.from(mentions)
}

const payloadString = (dispatch: GatewayDispatch, key: string): string | undefined => {
  const value = dispatch.payload[key]
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined
}

const demoAttachment = (): ShellAttachment => ({
  id: 'attachment-demo-image',
  filename: 'qa-snapshot.png',
  contentType: 'image/png',
  sizeBytes: 1_234,
  previewUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lLgNkwAAAABJRU5ErkJggg=='
})

export const useShellStore = defineStore('shell', {
  state: () => ({
    guild: {
      id: 'guild-discord-clone',
      name: 'Discord Clone'
    } satisfies ShellGuild,
    activeChannelId: 'channel-general',
    channelGroups: [
      {
        id: 'text-channels',
        name: 'Text Channels',
        channels: [
          {
            id: 'channel-general',
            name: 'general',
            type: 'GUILD_TEXT'
          },
          {
            id: 'channel-architecture',
            name: 'architecture',
            type: 'GUILD_TEXT'
          },
          {
            id: 'channel-qa-lab',
            name: 'qa-lab',
            type: 'GUILD_TEXT'
          },
          {
            id: 'channel-forum',
            name: 'forum',
            type: 'GUILD_FORUM'
          }
        ]
      },
      {
        id: 'voice-channels',
        name: 'Voice Channels',
        channels: [
          {
            id: 'channel-war-room',
            name: 'war-room',
            type: 'GUILD_VOICE'
          },
          {
            id: 'channel-pairing',
            name: 'pairing',
            type: 'GUILD_VOICE'
          }
        ]
      }
    ] satisfies ShellChannelGroup[],
    messages: [
      {
        id: 'message-general-welcome',
        channelId: 'channel-general',
        sequence: 1,
        author: 'vibe-coder',
        body: 'Welcome to the guild. This shell is wired for the first T00 QA pass. @cto-bot is tracking metadata.',
        createdAt: '2026-05-13T09:00:00.000Z',
        edited: true,
        pinned: true,
        deleted: false,
        mentions: ['cto-bot'],
        attachments: []
      },
      {
        id: 'message-general-deleted',
        channelId: 'channel-general',
        sequence: 2,
        author: 'cto-bot',
        body: '',
        createdAt: '2026-05-13T09:05:00.000Z',
        edited: false,
        pinned: false,
        deleted: true,
        mentions: [],
        attachments: []
      },
      {
        id: 'message-architecture-notes',
        channelId: 'channel-architecture',
        sequence: 3,
        author: 'cto-bot',
        body: 'Architecture notes belong in this channel.',
        createdAt: '2026-05-13T09:10:00.000Z',
        edited: false,
        pinned: false,
        deleted: false,
        mentions: [],
        attachments: []
      }
    ] satisfies ShellMessage[],
    expressions: [
      {
        key: 'wave',
        label: 'Wave',
        type: 'EMOJI',
        symbol: ':wave:',
        description: 'Friendly greeting emoji'
      },
      {
        key: 'shipit',
        label: 'Ship It',
        type: 'EMOJI',
        symbol: ':shipit:',
        description: 'Approval emoji for ready changes'
      },
      {
        key: 'approved',
        label: 'Approved sticker',
        type: 'STICKER',
        symbol: '[approved]',
        description: 'Sticker skeleton for approvals'
      }
    ] satisfies ShellExpression[],
    reactions: [] satisfies ShellReaction[],
    members: [
      {
        name: 'vibe-coder',
        status: 'online',
        roleIds: ['role-moderator']
      },
      {
        name: 'cto-bot',
        status: 'online',
        roleIds: []
      }
    ] satisfies ShellMember[],
    roles: [
      {
        id: 'role-everyone',
        name: '@everyone',
        permissions: ['VIEW_CHANNEL']
      },
      {
        id: 'role-moderator',
        name: 'Moderator',
        permissions: ['VIEW_CHANNEL', 'MANAGE_MESSAGES']
      },
      {
        id: 'role-engineering',
        name: 'Engineering',
        permissions: ['VIEW_CHANNEL']
      }
    ] satisfies ShellRole[],
    permissionOverwrites: [
      {
        channelId: 'channel-general',
        roleId: 'role-moderator',
        allow: ['SEND_MESSAGES'],
        deny: ['MANAGE_CHANNELS']
      }
    ] satisfies ShellPermissionOverwrite[],
    invitePreview: {
      code: 'discord-clone-t03',
      guildId: 'guild-discord-clone',
      channelId: 'channel-general',
      expiresIn: '7 days',
      maxUses: 12,
      uses: 0,
      roleGrantIds: ['role-moderator']
    } satisfies ShellInvitePreview,
    gateway: {
      status: 'READY',
      sessionId: 'gateway-session-t05',
      lastSequence: 42,
      lastHeartbeatAckAt: '2026-05-13T09:12:00.000Z',
      resumed: true,
      events: [
        {
          sequence: 42,
          type: 'READY',
          label: 'ready dispatch accepted'
        }
      ]
    } satisfies ShellGatewayState,
    social: {
      activeSelection: {
        type: 'GROUP',
        id: 'group-dm-t07-strike-team'
      },
      friends: [
        {
          id: 'user-cto-bot',
          name: 'cto-bot',
          status: 'FRIEND',
          directMessageId: 'dm-cto-bot'
        },
        {
          id: 'user-spam-drone',
          name: 'spam-drone',
          status: 'BLOCKED',
          directMessageId: 'dm-spam-drone'
        }
      ],
      directMessages: [
        {
          id: 'dm-cto-bot',
          userId: 'user-cto-bot',
          unreadCount: 2
        },
        {
          id: 'dm-spam-drone',
          userId: 'user-spam-drone',
          unreadCount: 0
        }
      ],
      groupDms: [
        {
          id: 'group-dm-t07-strike-team',
          name: 'T07 strike team',
          ownerId: 'vibe-coder',
          memberIds: ['vibe-coder', 'cto-bot'],
          call: {
            active: false,
            participants: []
          }
        }
      ]
    } satisfies ShellSocialState,
    forum: {
      activeForumChannelId: 'channel-forum',
      activeThreadId: 'thread-public-release-notes',
      postError: 'Ready',
      forums: [
        {
          channelId: 'channel-forum',
          guidelines: 'Use tags before posting and keep implementation threads scoped.',
          layout: 'LIST',
          tags: [
            {
              id: 'release',
              label: 'release',
              required: true
            },
            {
              id: 'help',
              label: 'help',
              required: false
            }
          ]
        }
      ],
      threads: [
        {
          id: 'thread-public-release-notes',
          parentChannelId: 'channel-forum',
          title: 'Release notes',
          type: 'PUBLIC',
          tagIds: ['release'],
          archived: false,
          autoArchiveAt: '2026-05-14T15:00:00.000Z',
          writeReceipt: 'Ready'
        },
        {
          id: 'thread-private-mod-review',
          parentChannelId: 'channel-forum',
          title: 'Moderator review',
          type: 'PRIVATE',
          tagIds: ['help'],
          archived: false,
          autoArchiveAt: '2026-05-14T16:00:00.000Z',
          writeReceipt: 'Ready'
        },
        {
          id: 'thread-archived-incident',
          parentChannelId: 'channel-forum',
          title: 'Archived incident follow-up',
          type: 'PUBLIC',
          tagIds: ['help'],
          archived: true,
          autoArchiveAt: '2026-05-13T15:00:00.000Z',
          writeReceipt: 'Thread is archived'
        }
      ]
    } satisfies ShellForumState,
    moderation: {
      onboardingQuestion: {
        id: 'onboarding-squad',
        prompt: 'Choose your squad',
        answers: [
          {
            id: 'engineering',
            label: 'Engineering squad',
            roleId: 'role-engineering',
            roleName: 'Engineering'
          }
        ]
      },
      selectedAnswerId: null,
      assignedRoleName: 'Unassigned',
      automodRules: [
        {
          id: 'keyword-leak',
          keyword: 'leak',
          enabled: true
        }
      ],
      decision: 'Ready',
      auditLogs: [
        {
          id: 'audit-rule-created',
          action: 'AUDIT_RULE_CREATED',
          detail: 'Keyword leak rule created',
          createdAt: '2026-05-14T12:00:00.000Z'
        }
      ]
    } satisfies ShellModerationState,
    voice: {
      activeChannelId: null,
      tokenProvider: 'LIVEKIT_SKELETON',
      token: null,
      participants: [],
      events: [
        {
          id: 'voice-ready',
          type: 'VOICE_READY',
          detail: 'Voice signaling skeleton ready'
        }
      ]
    } satisfies ShellVoiceState,
    presence: {
      nowMs: Date.parse('2026-05-13T09:12:00.000Z'),
      users: {
        'vibe-coder': {
          userId: 'vibe-coder',
          status: 'ONLINE',
          expiresAt: null
        },
        'cto-bot': {
          userId: 'cto-bot',
          status: 'IDLE',
          expiresAt: null
        },
        'spam-drone': {
          userId: 'spam-drone',
          status: 'OFFLINE',
          expiresAt: null
        }
      },
      typingByChannel: {
        'channel-general': [
          {
            userId: 'cto-bot',
            expiresAt: Date.parse('2026-05-13T09:17:00.000Z')
          }
        ]
      },
      readMarkers: {
        'channel-general': {
          channelId: 'channel-general',
          lastReadSequence: 2
        },
        'channel-architecture': {
          channelId: 'channel-architecture',
          lastReadSequence: 0
        },
        'channel-qa-lab': {
          channelId: 'channel-qa-lab',
          lastReadSequence: 0
        }
      }
    } satisfies ShellPresenceState,
    currentUser: 'vibe-coder',
    composerBody: '',
    stagedAttachment: null as ShellAttachment | null,
    voiceState: 'voice disconnected'
  }),
  getters: {
    activeChannel: (state): ShellChannel | undefined =>
      state.channelGroups
        .flatMap((group) => group.channels)
        .find((channel) => channel.id === state.activeChannelId),
    activeMessages: (state): ShellMessage[] =>
      state.messages.filter((message) => message.channelId === state.activeChannelId),
    presenceStatusForUser: (state) => (userId: string): ShellPresenceStatus => {
      const record = state.presence.users[userId]

      if (!record || (record.expiresAt !== null && record.expiresAt <= state.presence.nowMs)) {
        return 'OFFLINE'
      }

      return record.status
    },
    activeTypingUserIds: (state): string[] =>
      (state.presence.typingByChannel[state.activeChannelId] ?? [])
        .filter((typing) => typing.expiresAt > state.presence.nowMs)
        .map((typing) => typing.userId),
    unreadCountForChannel: (state) => (channelId: string): number => {
      const lastReadSequence = state.presence.readMarkers[channelId]?.lastReadSequence ?? 0

      return state.messages.filter(
        (message) =>
          message.channelId === channelId &&
          !message.deleted &&
          message.author !== state.currentUser &&
          message.sequence > lastReadSequence
      ).length
    },
    reactionsForMessage: (state) => (messageId: string): ShellReactionSummary[] =>
      state.reactions
        .filter((reaction) => reaction.messageId === messageId && reaction.reactorIds.length > 0)
        .map((reaction) => {
          const expression = state.expressions.find(
            (candidate) => candidate.key === reaction.emojiKey
          )

          return {
            messageId: reaction.messageId,
            emojiKey: reaction.emojiKey,
            label: expression?.label ?? reaction.emojiKey,
            symbol: expression?.symbol ?? reaction.emojiKey,
            count: new Set(reaction.reactorIds).size,
            reactedByCurrentUser: reaction.reactorIds.includes(state.currentUser)
          }
        }),
    memberRoleSummaries: (state) =>
      state.members.map((member) => ({
        ...member,
        roleNames: member.roleIds
          .map((roleId) => state.roles.find((role) => role.id === roleId)?.name)
          .filter((roleName): roleName is string => Boolean(roleName))
      })),
    activeChannelOverwriteSummaries: (state) => {
      const activeChannel = state.channelGroups
        .flatMap((group) => group.channels)
        .find((channel) => channel.id === state.activeChannelId)

      return state.permissionOverwrites
        .filter((overwrite) => overwrite.channelId === state.activeChannelId)
        .map((overwrite) => ({
          ...overwrite,
          channelLabel: activeChannel
            ? `${activeChannel.type === 'GUILD_TEXT' ? '#' : 'Voice'} ${activeChannel.name}`
            : 'No active channel',
          roleName: state.roles.find((role) => role.id === overwrite.roleId)?.name ?? overwrite.roleId
        }))
    },
    invitePreviewSummary: (state) => {
      const channel = state.channelGroups
        .flatMap((group) => group.channels)
        .find((candidate) => candidate.id === state.invitePreview.channelId)
      const roleNames = state.invitePreview.roleGrantIds.map(
        (roleId) => state.roles.find((role) => role.id === roleId)?.name ?? roleId
      )

      return {
        ...state.invitePreview,
        channelLabel: channel
          ? `${channel.type === 'GUILD_TEXT' ? '#' : 'Voice'} ${channel.name}`
          : 'No channel preview',
        guildName: state.guild.name,
        usesRemaining: state.invitePreview.maxUses - state.invitePreview.uses,
        roleNames
      }
    },
    gatewayHeartbeatAckLabel: (state) =>
      state.gateway.lastHeartbeatAckAt ? 'Heartbeat ACK received' : 'Awaiting heartbeat ACK',
    gatewayResumeLabel: (state) => (state.gateway.resumed ? 'Session resumed' : 'Fresh session'),
    socialDirectSummaries: (state) =>
      state.social.directMessages.map((dm) => ({
        ...dm,
        friend: state.social.friends.find((friend) => friend.id === dm.userId)
      })),
    activeGroupDm: (state): ShellGroupDm | undefined =>
      state.social.activeSelection.type === 'GROUP'
        ? state.social.groupDms.find((groupDm) => groupDm.id === state.social.activeSelection.id)
        : undefined,
    activeDirectFriend: (state): ShellFriend | undefined => {
      if (state.social.activeSelection.type !== 'DIRECT') {
        return undefined
      }

      const activeDm = state.social.directMessages.find(
        (dm) => dm.id === state.social.activeSelection.id
      )
      return activeDm
        ? state.social.friends.find((friend) => friend.id === activeDm.userId)
        : undefined
    },
    activeSocialLabel(): string {
      return this.activeGroupDm?.name ?? this.activeDirectFriend?.name ?? 'No DM selected'
    },
    activeForum: (state): ShellForum | undefined =>
      state.forum.forums.find((forum) => forum.channelId === state.forum.activeForumChannelId),
    activeForumThreads: (state): ShellThread[] =>
      state.forum.threads.filter(
        (thread) => thread.parentChannelId === state.forum.activeForumChannelId
      ),
    activeThread: (state): ShellThread | undefined =>
      state.forum.threads.find((thread) => thread.id === state.forum.activeThreadId),
    activeVoiceParticipant: (state): ShellVoiceParticipant | undefined =>
      state.voice.participants.find((participant) => participant.userId === state.currentUser),
    activeVoiceChannelName: (state): string =>
      state.channelGroups
        .flatMap((group) => group.channels)
        .find((channel) => channel.id === state.voice.activeChannelId)?.name ?? 'none'
  },
  actions: {
    setPresenceClock(nowMs: number) {
      this.presence.nowMs = nowMs
    },
    setUserPresence(userId: string, status: ShellPresenceStatus, ttlMs?: number) {
      this.presence.users[userId] = {
        userId,
        status,
        expiresAt: ttlMs === undefined ? null : this.presence.nowMs + ttlMs
      }
    },
    startTyping(channelId: string, userId: string, ttlMs = 5_000) {
      const currentTyping = this.presence.typingByChannel[channelId] ?? []
      this.presence.typingByChannel[channelId] = [
        ...currentTyping.filter((typing) => typing.userId !== userId),
        {
          userId,
          expiresAt: this.presence.nowMs + ttlMs
        }
      ]
    },
    stopTyping(channelId: string, userId: string) {
      this.presence.typingByChannel[channelId] = (this.presence.typingByChannel[channelId] ?? [])
        .filter((typing) => typing.userId !== userId)
    },
    stageDemoAttachment() {
      this.stagedAttachment = demoAttachment()
    },
    clearStagedAttachment() {
      this.stagedAttachment = null
    },
    addReaction(messageId: string, emojiKey: string, userId = this.currentUser): boolean {
      const messageExists = this.messages.some((message) => message.id === messageId && !message.deleted)
      if (!messageExists) {
        return false
      }

      const reaction = this.reactions.find(
        (candidate) => candidate.messageId === messageId && candidate.emojiKey === emojiKey
      )

      if (!reaction) {
        this.reactions.push({
          messageId,
          emojiKey,
          reactorIds: [userId]
        })
        return true
      }

      if (reaction.reactorIds.includes(userId)) {
        return false
      }

      reaction.reactorIds.push(userId)
      return true
    },
    removeReaction(messageId: string, emojiKey: string, userId = this.currentUser): boolean {
      const reaction = this.reactions.find(
        (candidate) => candidate.messageId === messageId && candidate.emojiKey === emojiKey
      )

      if (!reaction || !reaction.reactorIds.includes(userId)) {
        return false
      }

      reaction.reactorIds = reaction.reactorIds.filter((candidate) => candidate !== userId)
      this.reactions = this.reactions.filter((candidate) => candidate.reactorIds.length > 0)
      return true
    },
    toggleReaction(messageId: string, emojiKey: string, userId = this.currentUser): boolean {
      const reaction = this.reactions.find(
        (candidate) => candidate.messageId === messageId && candidate.emojiKey === emojiKey
      )

      if (reaction?.reactorIds.includes(userId)) {
        return this.removeReaction(messageId, emojiKey, userId)
      }

      return this.addReaction(messageId, emojiKey, userId)
    },
    markChannelRead(channelId: string) {
      const lastReadSequence = this.messages
        .filter((message) => message.channelId === channelId && !message.deleted)
        .reduce((highest, message) => Math.max(highest, message.sequence), 0)

      this.presence.readMarkers[channelId] = {
        channelId,
        lastReadSequence
      }
    },
    selectChannel(channelId: string) {
      const channelExists = this.channelGroups
        .flatMap((group) => group.channels)
        .some((channel) => channel.id === channelId)

      if (channelExists) {
        this.activeChannelId = channelId
        this.markChannelRead(channelId)
      }
    },
    sendMessage() {
      const body = this.composerBody.trim()
      const attachment = this.stagedAttachment

      if (!body && !attachment) {
        return
      }

      this.messages.push({
        id: `message-${Date.now()}`,
        channelId: this.activeChannelId,
        sequence: this.messages.reduce((highest, message) => Math.max(highest, message.sequence), 0) + 1,
        author: this.currentUser,
        body,
        createdAt: new Date().toISOString(),
        edited: false,
        pinned: false,
        deleted: false,
        mentions: extractMentions(body),
        attachments: attachment ? [{ ...attachment }] : []
      })
      this.composerBody = ''
      this.stagedAttachment = null
    },
    recordGatewayEvent(event: ShellGatewayEvent): boolean {
      if (event.sequence <= this.gateway.lastSequence) {
        return false
      }

      const alreadyRecorded = this.gateway.events.some(
        (candidate) => candidate.sequence === event.sequence
      )

      if (alreadyRecorded) {
        return false
      }

      this.gateway.events.push(event)
      this.gateway.lastSequence = Math.max(this.gateway.lastSequence, event.sequence)
      return true
    },
    applyGatewayDispatch(dispatch: unknown): boolean {
      const normalizedDispatch = normalizeGatewayDispatch(dispatch)
      if (!normalizedDispatch) {
        return false
      }

      const accepted = this.recordGatewayEvent(toShellGatewayEvent(normalizedDispatch))
      if (!accepted) {
        return false
      }

      this.applyGatewayDispatchState(normalizedDispatch)
      return true
    },
    applyGatewayDispatchState(dispatch: GatewayDispatch) {
      const sessionId = payloadString(dispatch, 'sessionId')
      if (sessionId) {
        this.gateway.sessionId = sessionId
      }

      if (dispatch.type === 'READY') {
        this.gateway.status = 'READY'
        this.gateway.resumed = false
        return
      }

      if (dispatch.type === 'HEARTBEAT_ACK') {
        this.gateway.status = 'READY'
        this.gateway.lastHeartbeatAckAt = payloadString(dispatch, 'acknowledgedAt') ?? dispatch.createdAt
        return
      }

      if (dispatch.type === 'RESUMED') {
        this.gateway.status = 'READY'
        this.gateway.resumed = true
        return
      }

      if (dispatch.type === 'DISCONNECTED' || dispatch.type === 'SESSION_CLOSED') {
        this.gateway.status = 'DISCONNECTED'
        this.gateway.resumed = false
      }
    },
    selectDirectDm(directMessageId: string): boolean {
      const dm = this.social.directMessages.find((candidate) => candidate.id === directMessageId)
      const friend = dm
        ? this.social.friends.find((candidate) => candidate.id === dm.userId)
        : undefined

      if (!dm || friend?.status === 'BLOCKED') {
        return false
      }

      this.social.activeSelection = {
        type: 'DIRECT',
        id: directMessageId
      }
      return true
    },
    selectGroupDm(groupDmId: string): boolean {
      const groupExists = this.social.groupDms.some((groupDm) => groupDm.id === groupDmId)

      if (!groupExists) {
        return false
      }

      this.social.activeSelection = {
        type: 'GROUP',
        id: groupDmId
      }
      return true
    },
    addGroupDmMember(groupDmId: string, memberId: string): boolean {
      const groupDm = this.social.groupDms.find((candidate) => candidate.id === groupDmId)

      if (!groupDm || groupDm.memberIds.includes(memberId)) {
        return false
      }

      groupDm.memberIds.push(memberId)
      return true
    },
    removeGroupDmMember(groupDmId: string, memberId: string): boolean {
      const groupDm = this.social.groupDms.find((candidate) => candidate.id === groupDmId)

      if (!groupDm || memberId === groupDm.ownerId) {
        return false
      }

      const nextMembers = groupDm.memberIds.filter((candidate) => candidate !== memberId)
      if (nextMembers.length === groupDm.memberIds.length) {
        return false
      }

      groupDm.memberIds = nextMembers
      groupDm.call.participants = groupDm.call.participants.filter(
        (candidate) => candidate !== memberId
      )
      return true
    },
    toggleActiveGroupCall(): boolean {
      const activeGroup = this.activeGroupDm

      if (!activeGroup) {
        return false
      }

      activeGroup.call.active = !activeGroup.call.active
      activeGroup.call.participants = activeGroup.call.active ? [this.currentUser] : []
      return true
    },
    selectThread(threadId: string): boolean {
      const thread = this.forum.threads.find((candidate) => candidate.id === threadId)
      if (!thread) {
        return false
      }

      this.forum.activeThreadId = threadId
      return true
    },
    reopenThread(threadId: string): boolean {
      const thread = this.forum.threads.find((candidate) => candidate.id === threadId)
      if (!thread) {
        return false
      }

      thread.archived = false
      thread.writeReceipt = 'Ready'
      return true
    },
    archiveThread(threadId: string): boolean {
      const thread = this.forum.threads.find((candidate) => candidate.id === threadId)
      if (!thread) {
        return false
      }

      thread.archived = true
      thread.writeReceipt = 'Thread is archived'
      return true
    },
    writeThreadMessage(threadId: string): boolean {
      const thread = this.forum.threads.find((candidate) => candidate.id === threadId)
      if (!thread) {
        return false
      }

      if (thread.archived) {
        thread.writeReceipt = 'Thread is archived'
        return false
      }

      thread.writeReceipt = 'Thread write accepted'
      this.forum.activeThreadId = thread.id
      return true
    },
    createForumPost(title: string, tagIds: string[]): boolean {
      const forum = this.activeForum
      if (!forum) {
        this.forum.postError = 'Forum unavailable'
        return false
      }

      const requiredTagIds = forum.tags.filter((tag) => tag.required).map((tag) => tag.id)
      const hasRequiredTag =
        requiredTagIds.length === 0 || requiredTagIds.some((tagId) => tagIds.includes(tagId))
      if (!hasRequiredTag) {
        this.forum.postError = 'Select at least one tag'
        return false
      }

      const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
      const id = `thread-forum-${slug}`
      if (!this.forum.threads.some((thread) => thread.id === id)) {
        this.forum.threads.push({
          id,
          parentChannelId: forum.channelId,
          title,
          type: 'PUBLIC',
          tagIds,
          archived: false,
          autoArchiveAt: '2026-05-14T18:00:00.000Z',
          writeReceipt: 'Ready'
        })
      }

      this.forum.activeThreadId = id
      this.forum.postError = 'Ready'
      return true
    },
    submitOnboardingAnswer(answerId: string): boolean {
      const answer = this.moderation.onboardingQuestion.answers.find(
        (candidate) => candidate.id === answerId
      )
      if (!answer) {
        return false
      }

      this.moderation.selectedAnswerId = answer.id
      this.moderation.assignedRoleName = answer.roleName
      const member = this.members.find((candidate) => candidate.name === this.currentUser)
      if (member && !member.roleIds.includes(answer.roleId)) {
        member.roleIds.push(answer.roleId)
      }
      this.appendAuditLog('ONBOARDING_ROLE_ASSIGNED', `${answer.roleName} assigned to ${this.currentUser}`)
      return true
    },
    simulateAutoModBlock(): boolean {
      const content = 'leak the release token'
      const matchedRule = this.moderation.automodRules.find(
        (rule) => rule.enabled && content.toLowerCase().includes(rule.keyword.toLowerCase())
      )

      if (!matchedRule) {
        this.moderation.decision = 'Allowed'
        return false
      }

      this.moderation.decision = 'Blocked before persist'
      this.appendAuditLog('AUTOMOD_MESSAGE_BLOCKED', `Blocked keyword ${matchedRule.keyword}`)
      return true
    },
    appendAuditLog(action: string, detail: string) {
      this.moderation.auditLogs.unshift({
        id: `audit-${Date.now()}-${this.moderation.auditLogs.length}`,
        action,
        detail,
        createdAt: new Date().toISOString()
      })
    },
    joinVoiceChannel(channelId: string): boolean {
      const channel = this.channelGroups
        .flatMap((group) => group.channels)
        .find((candidate) => candidate.id === channelId && candidate.type === 'GUILD_VOICE')
      if (!channel) {
        return false
      }

      const participant = {
        userId: this.currentUser,
        channelId,
        muted: false,
        deafened: false,
        speaking: false,
        screenSharing: false
      }
      this.voice.activeChannelId = channelId
      this.voice.token = `livekit-skeleton-${channelId}-${this.currentUser}`
      this.voice.participants = [
        ...this.voice.participants.filter((candidate) => candidate.userId !== this.currentUser),
        participant
      ]
      this.voiceState = `Voice connected: ${channel.name}`
      this.appendVoiceEvent('VOICE_JOIN', `${this.currentUser} joined ${channel.name}`)
      return true
    },
    leaveVoiceChannel(): boolean {
      if (!this.voice.activeChannelId) {
        return false
      }

      const channelName = this.activeVoiceChannelName
      this.voice.participants = this.voice.participants.filter(
        (participant) => participant.userId !== this.currentUser
      )
      this.voice.activeChannelId = null
      this.voice.token = null
      this.voiceState = 'voice disconnected'
      this.appendVoiceEvent('VOICE_LEAVE', `${this.currentUser} left ${channelName}`)
      return true
    },
    toggleVoiceFlag(flag: 'muted' | 'deafened' | 'speaking' | 'screenSharing'): boolean {
      const participant = this.activeVoiceParticipant
      if (!participant) {
        return false
      }

      participant[flag] = !participant[flag]
      this.appendVoiceEvent('VOICE_STATE_UPDATE', `${flag}=${participant[flag]}`)
      return true
    },
    appendVoiceEvent(type: string, detail: string) {
      this.voice.events.unshift({
        id: `voice-event-${Date.now()}-${this.voice.events.length}`,
        type,
        detail
      })
    }
  }
})
