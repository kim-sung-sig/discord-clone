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
        status: 'online'
      },
      {
        name: 'cto-bot',
        status: 'online'
      }
    ],
    currentUser: 'vibe-coder',
    voiceState: 'voice disconnected'
  }),
  getters: {
    activeChannel: (state): ShellChannel | undefined =>
      state.channelGroups
        .flatMap((group) => group.channels)
        .find((channel) => channel.id === state.activeChannelId),
    activeMessages: (state): ShellMessage[] =>
      state.messages.filter((message) => message.channelId === state.activeChannelId)
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
