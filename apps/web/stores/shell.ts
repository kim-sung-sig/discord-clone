import { defineStore } from 'pinia'

export const useShellStore = defineStore('shell', {
  state: () => ({
    guildName: 'Discord Clone',
    activeChannel: 'general',
    channelGroups: [
      {
        name: 'Text Channels',
        channels: ['general', 'architecture', 'qa-lab']
      },
      {
        name: 'Voice Channels',
        channels: ['war-room', 'pairing']
      }
    ],
    messages: [
      {
        author: 'vibe-coder',
        body: 'Welcome to the guild. This shell is wired for the first T00 QA pass.'
      }
    ],
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
  })
})
