import { defineStore } from 'pinia'
import {
  normalizeGatewayDispatch,
  toShellGatewayEvent,
  type GatewayDispatch
} from '../services/gateway-client'

export type ShellChannelType = 'GUILD_TEXT' | 'GUILD_VOICE'

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
  author: string
  body: string
  createdAt: string
  edited: boolean
  pinned: boolean
  deleted: boolean
  mentions: string[]
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
        author: 'vibe-coder',
        body: 'Welcome to the guild. This shell is wired for the first T00 QA pass. @cto-bot is tracking metadata.',
        createdAt: '2026-05-13T09:00:00.000Z',
        edited: true,
        pinned: true,
        deleted: false,
        mentions: ['cto-bot']
      },
      {
        id: 'message-general-deleted',
        channelId: 'channel-general',
        author: 'cto-bot',
        body: '',
        createdAt: '2026-05-13T09:05:00.000Z',
        edited: false,
        pinned: false,
        deleted: true,
        mentions: []
      },
      {
        id: 'message-architecture-notes',
        channelId: 'channel-architecture',
        author: 'cto-bot',
        body: 'Architecture notes belong in this channel.',
        createdAt: '2026-05-13T09:10:00.000Z',
        edited: false,
        pinned: false,
        deleted: false,
        mentions: []
      }
    ] satisfies ShellMessage[],
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
    currentUser: 'vibe-coder',
    composerBody: '',
    voiceState: 'voice disconnected'
  }),
  getters: {
    activeChannel: (state): ShellChannel | undefined =>
      state.channelGroups
        .flatMap((group) => group.channels)
        .find((channel) => channel.id === state.activeChannelId),
    activeMessages: (state): ShellMessage[] =>
      state.messages.filter((message) => message.channelId === state.activeChannelId),
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
    }
  },
  actions: {
    selectChannel(channelId: string) {
      const channelExists = this.channelGroups
        .flatMap((group) => group.channels)
        .some((channel) => channel.id === channelId)

      if (channelExists) {
        this.activeChannelId = channelId
      }
    },
    sendMessage() {
      const body = this.composerBody.trim()

      if (!body) {
        return
      }

      this.messages.push({
        id: `message-${Date.now()}`,
        channelId: this.activeChannelId,
        author: this.currentUser,
        body,
        createdAt: new Date().toISOString(),
        edited: false,
        pinned: false,
        deleted: false,
        mentions: extractMentions(body)
      })
      this.composerBody = ''
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
    }
  }
})
