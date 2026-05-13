import { defineStore } from 'pinia'

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

const extractMentions = (body: string): string[] => {
  const mentions = new Set<string>()
  const mentionPattern = /(?<![A-Za-z0-9_.<])@([A-Za-z0-9][A-Za-z0-9-]{0,31})/g
  for (const match of body.matchAll(mentionPattern)) {
    mentions.add(match[1].toLowerCase())
  }
  return Array.from(mentions)
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
    }
  }
})
