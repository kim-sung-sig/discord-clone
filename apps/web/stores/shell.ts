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
  channelId: string
  author: string
  body: string
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

export interface ShellMember {
  name: string
  status: string
  roleIds: string[]
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
        channelId: 'channel-general',
        author: 'vibe-coder',
        body: 'Welcome to the guild. This shell is wired for the first T00 QA pass.'
      },
      {
        channelId: 'channel-architecture',
        author: 'cto-bot',
        body: 'Architecture notes belong in this channel.'
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
    currentUser: 'vibe-coder',
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
    }
  }
})
