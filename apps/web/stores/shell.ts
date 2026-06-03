import { defineStore } from 'pinia'
import { useRuntimeConfig } from '#app'
import { DiscordRestError, createDiscordRestClient, discordApiPaths } from '../services/discord-api'
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

interface BackendGuildResponse {
  id: string
  name: string
}

interface BackendChannelResponse {
  id: string
  name: string
  type: ShellChannelType
}

interface BackendUserGuildResponse extends BackendGuildResponse {
  ownerId: string
  channels: Array<BackendChannelResponse & { parentId: string | null }>
}

interface BackendUserGuildsResponse {
  guilds: BackendUserGuildResponse[]
}

interface BackendMessageResponse {
  id: string
  channelId: string
  authorId: string
  content: string
  mentions: string[]
  pinned: boolean
  deleted: boolean
  edited: boolean
  createdAt: string
}

interface BackendMessagePageResponse {
  messages: BackendMessageResponse[]
  nextCursor: string | null
}

interface BackendAttachmentUploadResponse {
  attachmentId: string
  objectKey: string
  uploadUrl: string
}

interface BackendAttachmentResponse {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  objectKey: string
  status: string
}

interface BackendVoiceJoinResponse {
  participant: ShellVoiceParticipant
  token: {
    token: string | null
    provider: 'LIVEKIT_SKELETON'
  }
}

interface BackendStageSessionResponse {
  id: string
  channelId: string
  topic: string
  moderatorIds: string[]
  speakerIds: string[]
  audienceIds: string[]
  pendingSpeakerIds: string[]
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
  status?: 'sending' | 'sent' | 'failed'
  clientEventId?: string
  requestId?: string
  serverVersion?: number
}

type ShellMutationAction = 'MESSAGE_CREATE' | 'VOICE_JOIN' | 'STAGE_START'

export interface ShellPendingMutation {
  id: string
  action: ShellMutationAction
  entityId: string
  clientEventId: string
  requestId: string
  createdAt: string
}

export interface ShellFailedMutation extends ShellPendingMutation {
  reason: string
  failedAt: string
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

export interface ShellAdminPermissionDraft {
  roleId: string
  permission: string
  before: boolean
  after: boolean
}

export interface ShellAdminConsoleState {
  previewRoleId: string
  permissionDraft: ShellAdminPermissionDraft | null
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

export interface ShellStageSession {
  id: string
  channelId: string
  topic: string
  moderators: string[]
  speakers: string[]
  audience: string[]
  pendingRequests: string[]
}

export interface ShellSoundboardSound {
  id: string
  label: string
  emoji: string
}

export interface ShellSoundboardPlayEvent {
  id: string
  soundId: string
  soundLabel: string
  channelId: string
  channelName: string
  userId: string
}

export interface ShellPremiumEntitlement {
  userId: string
  featureKey: string
  grantedAt: string
}

export interface ShellCatalogItem {
  id: string
  featureKey: string
  label: string
  priceLabel: string
}

export interface ShellQuest {
  id: string
  title: string
  rewardFeatureKey: string
}

export interface ShellExperienceState {
  stageSession: ShellStageSession | null
  soundboardSounds: ShellSoundboardSound[]
  lastSoundboardEvent: ShellSoundboardPlayEvent | null
  entitlements: ShellPremiumEntitlement[]
  premiumGate: 'Locked' | 'Unlocked'
  catalog: ShellCatalogItem[]
  quests: ShellQuest[]
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

const payloadBoolean = (dispatch: GatewayDispatch, key: string): boolean | undefined => {
  const value = dispatch.payload[key]
  return typeof value === 'boolean' ? value : undefined
}

const payloadNumber = (dispatch: GatewayDispatch, key: string): number | undefined => {
  const value = dispatch.payload[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

const payloadStringArray = (dispatch: GatewayDispatch, key: string): string[] | undefined => {
  const value = dispatch.payload[key]
  return Array.isArray(value) && value.every((candidate) => typeof candidate === 'string')
    ? value
    : undefined
}

const nextShellMessageSequence = (messages: ShellMessage[]): number =>
  messages.reduce((highest, message) => Math.max(highest, message.sequence), 0) + 1

const createShellClientEventId = (): string => {
  const timePart = Date.now().toString(36)
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `web-shell:${timePart}:${randomPart}`
}

const gatewayEventId = (dispatch: GatewayDispatch): string => {
  const entityId =
    payloadString(dispatch, 'id') ??
    payloadString(dispatch, 'messageId') ??
    payloadString(dispatch, 'clientEventId') ??
    dispatch.channelId ??
    'gateway'
  const version = payloadNumber(dispatch, 'serverVersion') ?? payloadNumber(dispatch, 'version') ?? dispatch.sequence
  return `${dispatch.type}:${entityId}:${version}`
}

const demoAttachment = (): ShellAttachment => ({
  id: 'attachment-demo-image',
  filename: 'qa-snapshot.png',
  contentType: 'image/png',
  sizeBytes: 1_234,
  previewUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lLgNkwAAAABJRU5ErkJggg=='
})

const backendErrorMessage = (error: unknown): string => {
  if (error instanceof DiscordRestError) {
    const body = error.body as { message?: unknown } | undefined
    const detail = typeof body?.message === 'string' ? ` ${body.message}` : ''
    return `Discord API rejected the request (${error.status}).${detail}`
  }
  return 'Discord API is unavailable. Try again.'
}

const createShellRequestId = (): string => {
  const timePart = Date.now().toString(36)
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `web-shell-${timePart}-${randomPart}`
}

const attachmentUploadBody = (attachment: ShellAttachment): BodyInit => {
  const [, encoded = ''] = attachment.previewUrl.split(',', 2)
  return encoded || attachment.previewUrl
}

const toShellAttachment = (
  attachment: BackendAttachmentResponse,
  previewUrl: string
): ShellAttachment => ({
  id: attachment.id,
  filename: attachment.filename,
  contentType: attachment.contentType,
  sizeBytes: attachment.sizeBytes,
  previewUrl
})

export const useShellStore = defineStore('shell', {
  state: () => ({
    apiError: null as string | null,
    apiLastRequestId: null as string | null,
    apiBusy: false,
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
        permissions: ['VIEW_CHANNEL', 'MANAGE_MESSAGES', 'MANAGE_EXPRESSIONS']
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
    adminConsole: {
      previewRoleId: 'role-moderator',
      permissionDraft: {
        roleId: 'role-moderator',
        permission: 'MANAGE_CHANNELS',
        before: false,
        after: true
      }
    } satisfies ShellAdminConsoleState,
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
    latestGatewaySequence: 42,
    processedEventIds: ['READY:gateway:42'] as string[],
    pendingMutations: [] satisfies ShellPendingMutation[],
    failedMutations: [] satisfies ShellFailedMutation[],
    resyncRequired: false,
    messagePageCursors: {} as Record<string, string | null>,
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
    experience: {
      stageSession: null,
      soundboardSounds: [],
      lastSoundboardEvent: null,
      entitlements: [],
      premiumGate: 'Locked',
      catalog: [
        {
          id: 'catalog-hd-stream',
          featureKey: 'HD_STREAM',
          label: 'HD Stream Pack',
          priceLabel: 'Skeleton entitlement'
        }
      ],
      quests: [
        {
          id: 'quest-stream-10',
          title: 'Stream for 10 minutes',
          rewardFeatureKey: 'HD_STREAM'
        }
      ]
    } satisfies ShellExperienceState,
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
    activeMessagePageCursor: (state): string | null =>
      state.activeChannelId ? state.messagePageCursors[state.activeChannelId] ?? null : null,
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
    adminPermissionDiff: (state) => {
      const draft = state.adminConsole.permissionDraft
      if (!draft) {
        return null
      }
      const role = state.roles.find((candidate) => candidate.id === draft.roleId)
      return {
        ...draft,
        roleName: role?.name ?? draft.roleId,
        beforeLabel: draft.before ? 'allowed' : 'denied',
        afterLabel: draft.after ? 'allowed' : 'denied'
      }
    },
    previewAsRole: (state) => {
      const everyoneRole = state.roles.find((role) => role.id === 'role-everyone')
      const selectedRole = state.roles.find((role) => role.id === state.adminConsole.previewRoleId)
      const allowed = new Set<string>(everyoneRole?.permissions ?? [])
      for (const permission of selectedRole?.permissions ?? []) {
        allowed.add(permission)
      }

      const activeOverwrites = state.permissionOverwrites.filter(
        (overwrite) =>
          overwrite.channelId === state.activeChannelId &&
          (overwrite.roleId === 'role-everyone' || overwrite.roleId === state.adminConsole.previewRoleId)
      )
      const overwriteAllows = new Set<string>()
      for (const overwrite of activeOverwrites) {
        for (const permission of overwrite.deny) {
          allowed.delete(permission)
        }
      }
      for (const overwrite of activeOverwrites) {
        for (const permission of overwrite.allow) {
          overwriteAllows.add(permission)
          allowed.add(permission)
        }
      }

      const denied = new Set<string>()
      for (const overwrite of activeOverwrites) {
        for (const permission of overwrite.deny) {
          if (!allowed.has(permission)) {
            denied.add(permission)
          }
        }
      }

      return {
        roleName: selectedRole?.name ?? state.adminConsole.previewRoleId,
        allowed: Array.from(allowed).sort((left, right) => {
          const leftOverwrite = overwriteAllows.has(left)
          const rightOverwrite = overwriteAllows.has(right)
          if (leftOverwrite !== rightOverwrite) {
            return leftOverwrite ? -1 : 1
          }
          return left.localeCompare(right)
        }),
        denied: Array.from(denied).sort()
      }
    },
    privilegedAuditLogs: (state) =>
      state.moderation.auditLogs.filter((entry) =>
        ['ROLE_PERMISSION_UPDATED', 'ROLE_ASSIGNED', 'MESSAGE_DELETED', 'MESSAGE_PINNED', 'MESSAGE_UNPINNED'].includes(entry.action)
      ),
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
        .find((channel) => channel.id === state.voice.activeChannelId)?.name ?? 'none',
    canManageExpressions: (state): boolean => {
      const member = state.members.find((candidate) => candidate.name === state.currentUser)
      if (!member) {
        return false
      }

      return member.roleIds
        .map((roleId) => state.roles.find((role) => role.id === roleId))
        .some((role) => role?.permissions.includes('MANAGE_EXPRESSIONS'))
    }
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
    async sendMessage(bearerToken?: string | null) {
      const body = this.composerBody.trim()
      const attachment = this.stagedAttachment

      if (!body && !attachment) {
        return
      }
      if (!this.activeChannelId) {
        return
      }
      if (bearerToken && body && !attachment) {
        await this.sendBackendMessage(this.activeChannelId, body, bearerToken)
        return
      }
      if (bearerToken && body && attachment) {
        const uploadedAttachment = await this.uploadBackendAttachment(this.activeChannelId, attachment, bearerToken)
        if (!uploadedAttachment) {
          return
        }
        const message = await this.sendBackendMessage(this.activeChannelId, body, bearerToken, [uploadedAttachment])
        if (message) {
          await this.attachBackendAttachment(this.activeChannelId, message.id, uploadedAttachment, bearerToken)
        }
        return
      }

      this.messages.push({
        id: `message-${Date.now()}`,
        channelId: this.activeChannelId,
        sequence: nextShellMessageSequence(this.messages),
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
    async createBackendGuild(name: string, bearerToken: string): Promise<ShellGuild | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const guild = await client.post<BackendGuildResponse>(
          discordApiPaths.guild.create(),
          { name },
          { bearerToken, requestId }
        )
        this.guild = {
          id: guild.id,
          name: guild.name
        }
        return this.guild
      })
    },
    async loadCurrentUserGuilds(bearerToken: string): Promise<BackendUserGuildResponse[] | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const response = await client.get<BackendUserGuildsResponse>(
          discordApiPaths.auth.guilds(),
          { bearerToken, requestId }
        )
        const firstGuild = response.guilds[0]
        if (!firstGuild) {
          return response.guilds
        }

        this.guild = {
          id: firstGuild.id,
          name: firstGuild.name
        }

        const textChannels = firstGuild.channels
          .filter((channel) => channel.type !== 'GUILD_VOICE')
          .map((channel) => ({
            id: channel.id,
            name: channel.name,
            type: channel.type
          }) satisfies ShellChannel)
        const voiceChannels = firstGuild.channels
          .filter((channel) => channel.type === 'GUILD_VOICE')
          .map((channel) => ({
            id: channel.id,
            name: channel.name,
            type: channel.type
          }) satisfies ShellChannel)

        this.channelGroups = [
          {
            id: 'text-channels',
            name: 'Text Channels',
            channels: textChannels
          },
          {
            id: 'voice-channels',
            name: 'Voice Channels',
            channels: voiceChannels
          }
        ]

        const preferredActiveChannel = textChannels[0] ?? voiceChannels[0]
        if (preferredActiveChannel) {
          this.activeChannelId = preferredActiveChannel.id
          this.presence.readMarkers[preferredActiveChannel.id] ??= {
            channelId: preferredActiveChannel.id,
            lastReadSequence: 0
          }
        }

        return response.guilds
      })
    },
    async bootstrapCurrentUserWorkspace(bearerToken: string): Promise<BackendUserGuildResponse[] | null> {
      const guilds = await this.loadCurrentUserGuilds(bearerToken)
      if (!guilds) {
        return null
      }

      const activeChannel = this.activeChannel
      if (activeChannel && activeChannel.type !== 'GUILD_VOICE') {
        await this.resyncChannelMessages(activeChannel.id, bearerToken)
        this.markChannelRead(activeChannel.id)
      }

      return guilds
    },
    async createBackendChannel(
      guildId: string,
      name: string,
      type: ShellChannelType,
      bearerToken: string
    ): Promise<ShellChannel | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const channel = await client.post<BackendChannelResponse>(
          discordApiPaths.guild.createChannel(guildId),
          { name, type, parentId: null },
          { bearerToken, requestId }
        )
        const shellChannel: ShellChannel = {
          id: channel.id,
          name: channel.name,
          type: channel.type
        }
        const targetGroupId = channel.type === 'GUILD_VOICE' ? 'voice-channels' : 'text-channels'
        let group = this.channelGroups.find((candidate) => candidate.id === targetGroupId)
        if (!group) {
          group = {
            id: targetGroupId,
            name: channel.type === 'GUILD_VOICE' ? 'Voice Channels' : 'Text Channels',
            channels: []
          }
          this.channelGroups.push(group)
        }
        group.channels = [
          ...group.channels.filter((candidate) => candidate.id !== shellChannel.id),
          shellChannel
        ]
        if (channel.type !== 'GUILD_VOICE') {
          this.activeChannelId = channel.id
        }
        return shellChannel
      })
    },
    async uploadBackendAttachment(
      channelId: string,
      attachment: ShellAttachment,
      bearerToken: string
    ): Promise<ShellAttachment | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const upload = await client.post<BackendAttachmentUploadResponse>(
          discordApiPaths.attachment.uploads(),
          {
            channelId,
            filename: attachment.filename,
            contentType: attachment.contentType,
            sizeBytes: attachment.sizeBytes
          },
          { bearerToken, requestId }
        )
        const uploadResponse = await globalThis.fetch(upload.uploadUrl, {
          method: 'PUT',
          headers: {
            'Content-Type': attachment.contentType
          },
          body: attachmentUploadBody(attachment)
        })
        if (!uploadResponse.ok) {
          throw new Error(`attachment upload failed with ${uploadResponse.status}`)
        }
        const uploaded = await client.put<BackendAttachmentResponse>(
          discordApiPaths.attachment.uploaded(upload.attachmentId),
          undefined,
          { bearerToken, requestId }
        )
        return toShellAttachment(uploaded, attachment.previewUrl)
      })
    },
    async attachBackendAttachment(
      channelId: string,
      messageId: string,
      attachment: ShellAttachment,
      bearerToken: string
    ): Promise<ShellAttachment | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const attached = await client.post<BackendAttachmentResponse>(
          discordApiPaths.channel.messageAttachment(channelId, messageId, attachment.id),
          undefined,
          { bearerToken, requestId }
        )
        const shellAttachment = toShellAttachment(attached, attachment.previewUrl)
        this.messages = this.messages.map((message) => {
          if (message.id !== messageId) {
            return message
          }

          return {
            ...message,
            attachments: [
              ...message.attachments.filter((candidate) => candidate.id !== shellAttachment.id),
              shellAttachment
            ]
          }
        })
        return shellAttachment
      })
    },
    async sendBackendMessage(
      channelId: string,
      content: string,
      bearerToken: string,
      attachments: ShellAttachment[] = []
    ): Promise<ShellMessage | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const body = content.trim()
        if (!body) {
          return null
        }
        const clientEventId = createShellClientEventId()
        const localMessageId = `local-${clientEventId}`
        const optimisticMessage: ShellMessage = {
          id: localMessageId,
          channelId,
          sequence: nextShellMessageSequence(this.messages),
          author: this.currentUser,
          body,
          createdAt: new Date().toISOString(),
          edited: false,
          pinned: false,
          deleted: false,
          mentions: extractMentions(body),
          attachments: attachments.map((attachment) => ({ ...attachment })),
          status: 'sending',
          clientEventId,
          requestId
        }
        this.messages = [
          ...this.messages.filter((candidate) => candidate.id !== optimisticMessage.id),
          optimisticMessage
        ]
        this.pendingMutations.push({
          id: `pending-${clientEventId}`,
          action: 'MESSAGE_CREATE',
          entityId: optimisticMessage.id,
          clientEventId,
          requestId,
          createdAt: optimisticMessage.createdAt
        })

        try {
          const message = await client.post<BackendMessageResponse>(
            discordApiPaths.channel.messages(channelId),
            { content: body, idempotencyKey: clientEventId, clientEventId },
            { bearerToken, requestId }
          )
          const shellMessage = this.confirmBackendMessage(message, clientEventId, requestId)
          this.composerBody = ''
          this.stagedAttachment = null
          return shellMessage
        } catch (error) {
          this.failPendingMutation(clientEventId, backendErrorMessage(error))
          throw error
        }
      })
    },
    confirmBackendMessage(
      message: BackendMessageResponse,
      clientEventId: string,
      requestId: string
    ): ShellMessage {
      const existing = this.messages.find(
        (candidate) => candidate.id === message.id || candidate.clientEventId === clientEventId
      )
      const shellMessage: ShellMessage = {
        id: message.id,
        channelId: message.channelId,
        sequence: existing?.sequence ?? nextShellMessageSequence(this.messages),
        author: message.authorId,
        body: message.content,
        createdAt: message.createdAt,
        edited: message.edited,
        pinned: message.pinned,
        deleted: message.deleted,
        mentions: message.mentions,
        attachments: existing?.attachments ?? [],
        status: 'sent',
        clientEventId,
        requestId,
        serverVersion: existing?.serverVersion
      }
      this.messages = [
        ...this.messages.filter(
          (candidate) => candidate.id !== shellMessage.id && candidate.clientEventId !== clientEventId
        ),
        shellMessage
      ]
      this.pendingMutations = this.pendingMutations.filter(
        (candidate) => candidate.clientEventId !== clientEventId
      )
      return shellMessage
    },
    failPendingMutation(clientEventId: string, reason: string) {
      const failed = this.pendingMutations.find((candidate) => candidate.clientEventId === clientEventId)
      this.pendingMutations = this.pendingMutations.filter(
        (candidate) => candidate.clientEventId !== clientEventId
      )
      this.messages = this.messages.filter((candidate) => candidate.clientEventId !== clientEventId)

      if (!failed) {
        return
      }

      this.failedMutations = [
        ...this.failedMutations.filter((candidate) => candidate.clientEventId !== clientEventId),
        {
          ...failed,
          reason,
          failedAt: new Date().toISOString()
        }
      ]
    },
    async resyncChannelMessages(channelId: string, bearerToken: string, limit = 50): Promise<ShellMessage[] | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const page = await client.get<BackendMessagePageResponse>(
          discordApiPaths.channel.messages(channelId, { limit }),
          { bearerToken, requestId }
        )
        const nextSequence = nextShellMessageSequence(this.messages)
        const messages = page.messages.map((message, index) => {
          const existing = this.messages.find((candidate) => candidate.id === message.id)
          return {
            id: message.id,
            channelId: message.channelId,
            sequence: existing?.sequence ?? nextSequence + index,
            author: message.authorId,
            body: message.content,
            createdAt: message.createdAt,
            edited: message.edited,
            pinned: message.pinned,
            deleted: message.deleted,
            mentions: message.mentions,
            attachments: existing?.attachments ?? [],
            status: 'sent'
          } satisfies ShellMessage
        })
        this.messages = [
          ...this.messages.filter((message) => message.channelId !== channelId),
          ...messages
        ]
        this.messagePageCursors[channelId] = page.nextCursor
        this.resyncRequired = false
        return messages
      })
    },
    async loadOlderChannelMessages(channelId: string, bearerToken: string, limit = 50): Promise<ShellMessage[] | null> {
      const before = this.messagePageCursors[channelId]
      if (!before) {
        return []
      }

      return this.withBackendRequest(async (client, requestId) => {
        const page = await client.get<BackendMessagePageResponse>(
          discordApiPaths.channel.messages(channelId, { before, limit }),
          { bearerToken, requestId }
        )
        const existingIds = new Set(this.messages.map((message) => message.id))
        const nextSequence = nextShellMessageSequence(this.messages)
        const olderMessages = page.messages
          .filter((message) => !existingIds.has(message.id))
          .map((message, index) => ({
            id: message.id,
            channelId: message.channelId,
            sequence: nextSequence + index,
            author: message.authorId,
            body: message.content,
            createdAt: message.createdAt,
            edited: message.edited,
            pinned: message.pinned,
            deleted: message.deleted,
            mentions: message.mentions,
            attachments: [],
            status: 'sent'
          }) satisfies ShellMessage)

        this.messages = [
          ...olderMessages,
          ...this.messages
        ]
        this.messagePageCursors[channelId] = page.nextCursor
        return olderMessages
      })
    },
    async loadOlderActiveChannelMessages(bearerToken: string): Promise<ShellMessage[] | null> {
      if (!this.activeChannelId) {
        return []
      }

      return this.loadOlderChannelMessages(this.activeChannelId, bearerToken)
    },
    async joinBackendVoice(channelId: string, bearerToken: string): Promise<BackendVoiceJoinResponse | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const response = await client.post<BackendVoiceJoinResponse>(
          `/api/voice/channels/${encodeURIComponent(channelId)}/join`,
          undefined,
          { bearerToken, requestId }
        )
        this.voice.activeChannelId = response.participant.channelId
        this.voice.token = response.token.token
        this.voice.tokenProvider = response.token.provider
        this.voice.participants = [
          ...this.voice.participants.filter((candidate) => candidate.userId !== response.participant.userId),
          response.participant
        ]
        this.voiceState = `Voice connected: ${this.activeVoiceChannelName}`
        this.appendVoiceEvent('VOICE_JOIN', `${response.participant.userId} joined ${this.activeVoiceChannelName}`)
        return response
      })
    },
    async startBackendStage(
      channelId: string,
      topic: string,
      bearerToken: string
    ): Promise<ShellStageSession | null> {
      return this.withBackendRequest(async (client, requestId) => {
        const session = await client.post<BackendStageSessionResponse>(
          `/api/stage/channels/${encodeURIComponent(channelId)}/sessions`,
          { topic },
          { bearerToken, requestId }
        )
        this.experience.stageSession = {
          id: session.id,
          channelId: session.channelId,
          topic: session.topic,
          moderators: session.moderatorIds,
          speakers: session.speakerIds,
          audience: session.audienceIds,
          pendingRequests: session.pendingSpeakerIds
        }
        return this.experience.stageSession
      })
    },
    async withBackendRequest<T>(
      operation: (client: ReturnType<typeof createDiscordRestClient>, requestId: string) => Promise<T>
    ): Promise<T | null> {
      this.apiError = null
      const requestId = createShellRequestId()
      this.apiLastRequestId = requestId
      this.apiBusy = true
      try {
        const config = useRuntimeConfig()
        return await operation(createDiscordRestClient({
          baseUrl: config.public.apiBaseUrl,
          fetcher: globalThis.fetch
        }), requestId)
      } catch (error) {
        this.apiError = `${backendErrorMessage(error)} Request id: ${requestId}`
        return null
      } finally {
        this.apiBusy = false
      }
    },
    recordGatewayEvent(event: ShellGatewayEvent): boolean {
      if (event.sequence <= this.latestGatewaySequence) {
        return false
      }

      if (event.sequence > this.latestGatewaySequence + 1) {
        this.resyncRequired = true
        return false
      }

      const alreadyRecorded = this.gateway.events.some(
        (candidate) => candidate.sequence === event.sequence
      )

      if (alreadyRecorded) {
        return false
      }

      this.gateway.events.push(event)
      this.latestGatewaySequence = event.sequence
      this.gateway.lastSequence = event.sequence
      return true
    },
    applyGatewayDispatch(dispatch: unknown): boolean {
      const normalizedDispatch = normalizeGatewayDispatch(dispatch)
      if (!normalizedDispatch) {
        return false
      }

      const eventId = gatewayEventId(normalizedDispatch)
      if (this.processedEventIds.includes(eventId)) {
        return false
      }

      const accepted = this.recordGatewayEvent(toShellGatewayEvent(normalizedDispatch))
      if (!accepted) {
        return false
      }

      this.processedEventIds = [...this.processedEventIds.slice(-255), eventId]
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

      if (dispatch.type === 'MESSAGE_CREATE') {
        this.applyGatewayMessageCreate(dispatch)
        return
      }

      if (dispatch.type === 'MESSAGE_UPDATE') {
        this.applyGatewayMessageUpdate(dispatch)
        return
      }

      if (dispatch.type === 'MESSAGE_DELETE') {
        this.applyGatewayMessageDelete(dispatch)
        return
      }

      if (dispatch.type === 'DISCONNECTED' || dispatch.type === 'SESSION_CLOSED') {
        this.gateway.status = 'DISCONNECTED'
        this.gateway.resumed = false
      }
    },
    applyGatewayMessageCreate(dispatch: GatewayDispatch) {
      const messageId = payloadString(dispatch, 'id') ?? payloadString(dispatch, 'messageId')
      const channelId = dispatch.channelId || payloadString(dispatch, 'channelId')
      const body = payloadString(dispatch, 'content') ?? payloadString(dispatch, 'body') ?? ''

      if (!messageId || !channelId) {
        return
      }

      const clientEventId = payloadString(dispatch, 'clientEventId')
      const requestId = payloadString(dispatch, 'requestId')
      const serverVersion = payloadNumber(dispatch, 'serverVersion') ?? payloadNumber(dispatch, 'version')
      const existing = this.messages.find(
        (candidate) => candidate.id === messageId || candidate.clientEventId === clientEventId
      )
      const message: ShellMessage = {
        id: messageId,
        channelId,
        sequence: payloadNumber(dispatch, 'messageSequence') ?? existing?.sequence ?? nextShellMessageSequence(this.messages),
        author: payloadString(dispatch, 'authorId') ?? payloadString(dispatch, 'author') ?? existing?.author ?? 'unknown',
        body,
        createdAt: payloadString(dispatch, 'createdAt') ?? existing?.createdAt ?? dispatch.createdAt,
        edited: payloadBoolean(dispatch, 'edited') ?? existing?.edited ?? false,
        pinned: payloadBoolean(dispatch, 'pinned') ?? existing?.pinned ?? false,
        deleted: payloadBoolean(dispatch, 'deleted') ?? existing?.deleted ?? false,
        mentions: payloadStringArray(dispatch, 'mentions') ?? extractMentions(body),
        attachments: existing?.attachments ?? [],
        status: 'sent',
        ...(clientEventId ? { clientEventId } : {}),
        ...(requestId ? { requestId } : {}),
        ...(serverVersion !== undefined ? { serverVersion } : existing?.serverVersion !== undefined ? { serverVersion: existing.serverVersion } : {})
      }

      this.messages = [
        ...this.messages.filter(
          (candidate) => candidate.id !== message.id && candidate.clientEventId !== clientEventId
        ),
        message
      ]
      if (clientEventId) {
        this.pendingMutations = this.pendingMutations.filter(
          (candidate) => candidate.clientEventId !== clientEventId
        )
      }
    },
    applyGatewayMessageUpdate(dispatch: GatewayDispatch) {
      const messageId = payloadString(dispatch, 'id') ?? payloadString(dispatch, 'messageId')
      if (!messageId) {
        return
      }

      const message = this.messages.find((candidate) => candidate.id === messageId)
      if (!message) {
        return
      }

      const serverVersion = payloadNumber(dispatch, 'serverVersion') ?? payloadNumber(dispatch, 'version')
      if (serverVersion !== undefined && message.serverVersion !== undefined && serverVersion < message.serverVersion) {
        return
      }

      const body = payloadString(dispatch, 'content') ?? payloadString(dispatch, 'body')
      if (body !== undefined) {
        message.body = body
        message.mentions = payloadStringArray(dispatch, 'mentions') ?? extractMentions(body)
      }
      message.edited = payloadBoolean(dispatch, 'edited') ?? true
      if (payloadBoolean(dispatch, 'pinned') !== undefined) {
        message.pinned = payloadBoolean(dispatch, 'pinned')!
      }
      if (serverVersion !== undefined) {
        message.serverVersion = serverVersion
      }
      message.status = 'sent'
    },
    applyGatewayMessageDelete(dispatch: GatewayDispatch) {
      const messageId = payloadString(dispatch, 'id') ?? payloadString(dispatch, 'messageId')
      if (!messageId) {
        return
      }

      const message = this.messages.find((candidate) => candidate.id === messageId)
      if (!message) {
        return
      }

      const serverVersion = payloadNumber(dispatch, 'serverVersion') ?? payloadNumber(dispatch, 'version')
      if (serverVersion !== undefined && message.serverVersion !== undefined && serverVersion < message.serverVersion) {
        return
      }

      message.body = ''
      message.deleted = true
      message.edited = false
      message.pinned = false
      message.mentions = []
      message.status = 'sent'
      if (serverVersion !== undefined) {
        message.serverVersion = serverVersion
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
    applyPermissionDraft(): boolean {
      const draft = this.adminConsole.permissionDraft
      if (!draft) {
        return false
      }

      const role = this.roles.find((candidate) => candidate.id === draft.roleId)
      if (!role) {
        return false
      }

      if (draft.after && !role.permissions.includes(draft.permission)) {
        role.permissions.push(draft.permission)
      }
      if (!draft.after) {
        role.permissions = role.permissions.filter((permission) => permission !== draft.permission)
      }

      const roleName = role.name
      this.appendAuditLog('ROLE_PERMISSION_UPDATED', `${roleName} ${draft.permission} changed to ${draft.after ? 'allowed' : 'denied'}`)
      this.adminConsole.permissionDraft = {
        ...draft,
        before: draft.after
      }
      return true
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
    },
    startStageSession(channelId = 'channel-war-room', topic = 'T14 roadmap live review'): boolean {
      const channel = this.channelGroups
        .flatMap((group) => group.channels)
        .find((candidate) => candidate.id === channelId && candidate.type === 'GUILD_VOICE')
      if (!channel) {
        return false
      }

      this.experience.stageSession = {
        id: `stage-${channelId}`,
        channelId,
        topic,
        moderators: [this.currentUser],
        speakers: [],
        audience: [],
        pendingRequests: []
      }
      return true
    },
    requestToSpeak(userId = this.currentUser): boolean {
      const session = this.experience.stageSession
      if (!session) {
        return false
      }

      if (!session.audience.includes(userId)) {
        session.audience.push(userId)
      }
      if (!session.speakers.includes(userId) && !session.pendingRequests.includes(userId)) {
        session.pendingRequests.push(userId)
      }
      return true
    },
    approveStageSpeaker(userId: string): boolean {
      const session = this.experience.stageSession
      if (!session || !session.pendingRequests.includes(userId)) {
        return false
      }

      session.pendingRequests = session.pendingRequests.filter((candidate) => candidate !== userId)
      session.audience = session.audience.filter((candidate) => candidate !== userId)
      if (!session.speakers.includes(userId)) {
        session.speakers.push(userId)
      }
      return true
    },
    moveStageAudience(userId: string): boolean {
      const session = this.experience.stageSession
      if (!session) {
        return false
      }

      session.speakers = session.speakers.filter((candidate) => candidate !== userId)
      session.pendingRequests = session.pendingRequests.filter((candidate) => candidate !== userId)
      if (!session.audience.includes(userId)) {
        session.audience.push(userId)
      }
      return true
    },
    createSoundboardSound(): boolean {
      if (!this.canManageExpressions) {
        return false
      }

      if (!this.experience.soundboardSounds.some((sound) => sound.id === 'sound-applause')) {
        this.experience.soundboardSounds.push({
          id: 'sound-applause',
          label: 'Applause',
          emoji: ':clap:'
        })
      }
      return true
    },
    playSoundboardSound(soundId = 'sound-applause', channelId = 'channel-war-room'): boolean {
      const sound = this.experience.soundboardSounds.find((candidate) => candidate.id === soundId)
      const channel = this.channelGroups
        .flatMap((group) => group.channels)
        .find((candidate) => candidate.id === channelId && candidate.type === 'GUILD_VOICE')
      if (!sound || !channel) {
        return false
      }

      this.experience.lastSoundboardEvent = {
        id: `sound-play-${Date.now()}`,
        soundId: sound.id,
        soundLabel: sound.label,
        channelId: channel.id,
        channelName: channel.name,
        userId: this.currentUser
      }
      return true
    },
    grantPremiumEntitlement(featureKey = 'HD_STREAM', userId = this.currentUser): boolean {
      const exists = this.experience.entitlements.some(
        (entitlement) => entitlement.userId === userId && entitlement.featureKey === featureKey
      )
      if (exists) {
        return false
      }

      this.experience.entitlements.push({
        userId,
        featureKey,
        grantedAt: new Date().toISOString()
      })
      return true
    },
    checkPremiumFeature(featureKey = 'HD_STREAM', userId = this.currentUser): boolean {
      const enabled = this.experience.entitlements.some(
        (entitlement) => entitlement.userId === userId && entitlement.featureKey === featureKey
      )
      this.experience.premiumGate = enabled ? 'Unlocked' : 'Locked'
      return enabled
    }
  }
})
