import {
  useShellStore,
  type ShellAdminConsoleState,
  type ShellChannelGroup,
  type ShellExperienceState,
  type ShellForumState,
  type ShellGatewayState,
  type ShellGuild,
  type ShellInvitePreview,
  type ShellMember,
  type ShellMessage,
  type ShellModerationState,
  type ShellPermissionOverwrite,
  type ShellPresenceState,
  type ShellRole,
  type ShellSocialState,
  type ShellVoiceState
} from '../../stores/shell'

/**
 * Demo workspace that used to be the shell store default state.
 * Test-only: a real session starts from the empty store default.
 */
export const createShellDemoState = () => ({
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
      body: 'Architecture notes belong in this channel. @vibe-coder should review the next API contract.',
      createdAt: '2026-05-13T09:10:00.000Z',
      edited: false,
      pinned: false,
      deleted: false,
      mentions: ['vibe-coder'],
      attachments: []
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
    messageReports: [],
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
  currentUser: 'vibe-coder'
})

/** Resets the shell store and seeds the demo workspace explicitly. */
export const seedShellDemoState = (shell = useShellStore()) => {
  shell.$reset()
  shell.$patch(createShellDemoState())
  return shell
}
