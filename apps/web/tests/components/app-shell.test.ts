import { mountSuspended } from '@nuxt/test-utils/runtime'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../../pages/app.vue'
import { useAuthStore } from '../../stores/auth'
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' }
  })

describe('Discord app shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    useAuthStore().$reset()
    useShellStore().$reset()
    usePreferencesStore().$reset()
    window.localStorage.clear()
  })

  it('renders a VS Code-inspired user workspace as the primary shell', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="vscode-shell"]').attributes('data-theme-id')).toBe('vscode-dark')
    expect(wrapper.get('[data-testid="activity-bar"]').text()).toContain('Explorer')
    expect(wrapper.get('[data-testid="workspace-explorer"]').text()).toContain('Text Channels')
    expect(wrapper.get('[data-testid="editor-titlebar"]').text()).toContain('# general')
    expect(wrapper.get('[data-testid="editor-chat"]').find('[data-testid="message-input"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="bottom-panel"]').text()).toContain('Gateway')
    expect(wrapper.get('[data-testid="status-bar"]').text()).toContain('READY')
    expect(wrapper.get('[data-testid="legacy-shell-contracts"]').attributes('aria-hidden')).toBe('true')
  })

  it('switches VS Code activity views without leaving the authenticated app shell', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('explorer')
    expect(wrapper.get('[data-testid="activity-explorer"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="editor-chat"]').find('[data-testid="message-input"]').exists()).toBe(true)

    await wrapper.get('[data-testid="activity-search"]').trigger('click')

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('search')
    expect(wrapper.get('[data-testid="activity-search"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="workbench-search-view"]').text()).toContain('Search messages and channels')
    expect(wrapper.get('[data-testid="workbench-search-results"]').text()).toContain('general')
    expect(wrapper.find('[data-testid="editor-chat"]').exists()).toBe(false)

    await wrapper.get('[data-testid="activity-inbox"]').trigger('click')

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('inbox')
    expect(wrapper.get('[data-testid="activity-inbox"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="workbench-inbox-view"]').text()).toContain('Notification inbox')
    expect(wrapper.get('[data-testid="workbench-inbox-mention-message-architecture-notes"]').text()).toContain('architecture')
    expect(wrapper.get('[data-testid="workbench-inbox-unread-channel-architecture"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="workbench-inbox-dm-dm-cto-bot"]').text()).toContain('2')

    await wrapper.get('[data-testid="activity-calls"]').trigger('click')

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('calls')
    expect(wrapper.get('[data-testid="workbench-calls-view"]').text()).toContain('Voice rooms')
    expect(wrapper.get('[data-testid="workbench-calls-view"]').text()).toContain('war-room')

    await wrapper.get('[data-testid="activity-settings"]').trigger('click')

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('settings')
    expect(wrapper.get('[data-testid="workbench-settings-view"]').text()).toContain('Workspace settings')
    expect(wrapper.get('[data-testid="settings-locale-select"]').element).toBeInstanceOf(HTMLSelectElement)

    await wrapper.get('[data-testid="locale-select"]').setValue('ko-KR')
    await nextTick()

    expect(wrapper.get('[data-testid="workbench-settings-view"]').text()).not.toContain('Workspace settings')
    expect(wrapper.get('[data-testid="workbench-settings-view"]').text()).not.toContain('Theme')
  })

  it('switches locale and theme from JSON-backed user preferences', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="locale-select"]').setValue('ko-KR')
    await wrapper.get('[data-testid="theme-select"]').setValue('cosmos')
    await nextTick()

    expect(wrapper.get('[data-testid="workspace-explorer"]').text()).toContain('텍스트 채널')
    expect(wrapper.get('[data-testid="editor-empty-hint"]').text()).toContain('VS Code 스타일')
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).not.toContain('Members')
    expect(wrapper.get('[data-testid="gateway-status-panel"]').text()).not.toContain('Gateway')
    expect(wrapper.get('[data-testid="voice-token-provider"]').text()).not.toContain('Provider')
    expect(wrapper.get('[data-testid="voice-join-channel-war-room"]').text()).not.toContain('Join')
    expect(wrapper.get('[data-testid="voice-leave"]').text()).not.toContain('Leave')
    expect(wrapper.get('[data-testid="attachment-stage-demo"]').text()).not.toContain('Attach image')
    expect(wrapper.get('[data-testid="message-send"]').text()).not.toContain('Send')
    expect(wrapper.get('[data-testid="expression-toggle-message-general-welcome"]').text()).not.toContain('Add reaction')
    expect(wrapper.get('[data-testid="vscode-shell"]').attributes('data-locale')).toBe('ko-KR')
    expect(wrapper.get('[data-testid="vscode-shell"]').attributes('data-theme-id')).toBe('cosmos')
    expect(document.documentElement.dataset.theme).toBe('cosmos')
    expect(document.documentElement.style.getPropertyValue('--workbench-bg')).toBe('#0f141d')
    expect((wrapper.get('[data-testid="vscode-shell"]').element as HTMLElement).style.getPropertyValue('--workbench-bg')).toBe('#0f141d')
  })

  it('renders guild, grouped channels, active channel, member sidebar, and user panel', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="server-rail"]').text()).toContain('Discord Clone')
    expect(wrapper.get('[data-testid="guild-name"]').text()).toContain('Discord Clone')
    expect(wrapper.get('[data-testid="channel-general"]').text()).toContain('#')
    expect(wrapper.get('[data-testid="channel-general"]').text()).toContain('general')
    expect(wrapper.get('[data-testid="channel-war-room"]').text()).toContain('Voice')
    expect(wrapper.get('[data-testid="channel-war-room"]').text()).toContain('war-room')
    expect(wrapper.get('[data-testid="active-channel"]').text()).toContain('# general')
    expect(wrapper.get('[data-channel-id="channel-general"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Welcome to the guild')
    expect(wrapper.get('[data-testid="message-pinned-label"]').text()).toContain('Pinned')
    expect(wrapper.get('[data-testid="message-edited-marker"]').text()).toContain('edited')
    expect(wrapper.get('[data-testid="message-tombstone"]').text()).toContain('message deleted')
    expect(wrapper.get('[data-testid="mention-chip-cto-bot"]').text()).toContain('@cto-bot')
    expect(wrapper.get('[data-testid="message-input"]').attributes('placeholder')).toContain('Message # general')
    expect(wrapper.get('[data-testid="message-list"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="chat-viewport"]').find('[data-testid="message-composer"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('Members')
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('online')
    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('vibe-coder')
  })

  it('renders deterministic presence badges for members', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    expect(wrapper.get('[data-testid="presence-badge-vibe-coder"]').text()).toContain('Online')
    expect(wrapper.get('[data-testid="presence-badge-cto-bot"]').text()).toContain('Idle')
  })

  it('renders typing users for the active channel only', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    expect(wrapper.get('[data-testid="typing-indicator"]').text()).toContain('cto-bot is typing')

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')

    expect(wrapper.find('[data-testid="typing-indicator"]').exists()).toBe(false)
  })

  it('renders unread badges and clears channel unread state when selected', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    expect(wrapper.get('[data-testid="unread-badge-channel-architecture"]').text()).toContain('1')

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')

    expect(wrapper.find('[data-testid="unread-badge-channel-architecture"]').exists()).toBe(false)
  })

  it('updates read markers through store-backed mark-read actions', async () => {
    await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()

    expect((shell as any).unreadCountForChannel?.('channel-architecture')).toBe(1)

    ;(shell as any).markChannelRead?.('channel-architecture')

    expect((shell as any).unreadCountForChannel?.('channel-architecture')).toBe(0)
  })

  it('opens mention inbox rows and clears channel unread state', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()
    await nextTick()

    await wrapper.get('[data-testid="activity-inbox"]').trigger('click')
    expect(wrapper.get('[data-testid="workbench-inbox-mention-message-architecture-notes"]').text()).toContain('cto-bot')

    await wrapper.get('[data-testid="workbench-inbox-unread-channel-architecture"] button').trigger('click')

    expect(wrapper.get('[data-testid="workspace"]').attributes('data-active-workbench-view')).toBe('explorer')
    expect(shell.activeChannelId).toBe('channel-architecture')
    expect((shell as any).unreadCountForChannel?.('channel-architecture')).toBe(0)
  })

  it('searches messages and drives the local moderation report queue', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()

    await wrapper.get('[data-testid="activity-search"]').trigger('click')
    await wrapper.get('[data-testid="workbench-search-input"]').setValue('API contract')
    await nextTick()

    expect(wrapper.get('[data-testid="workbench-search-message-message-architecture-notes"]').text()).toContain('API contract')
    expect(wrapper.get('[data-testid="workbench-search-message-message-architecture-notes"]').text()).toContain('cto-bot')

    await wrapper.get('[data-testid="workbench-report-message-message-architecture-notes"]').trigger('click')

    expect(wrapper.get('[data-testid="workbench-report-queue"]').text()).toContain('Needs moderator review')
    expect(shell.openMessageReports).toHaveLength(1)
    expect(shell.moderation.auditLogs[0].action).toBe('MESSAGE_REPORTED')

    const reportId = shell.openMessageReports[0]!.id
    await wrapper.get(`[data-testid="workbench-resolve-report-${reportId}"]`).trigger('click')

    expect(shell.openMessageReports).toHaveLength(0)
    expect(shell.moderation.auditLogs[0].action).toBe('MESSAGE_REPORT_RESOLVED')
  })

  it('reports and resolves messages through the backend when a bearer token is available', async () => {
    await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      if (String(input).endsWith('/api/guilds/guild-discord-clone/channels/channel-architecture/messages/message-architecture-notes/reports')) {
        return jsonResponse({
          id: 'report-backend-1',
          channelId: 'channel-architecture',
          messageId: 'message-architecture-notes',
          reporterId: 'user-backend',
          reason: 'MODERATOR_REVIEW',
          status: 'OPEN',
          moderatorId: null,
          resolution: '',
          createdAt: '2026-06-30T00:00:00.000Z',
          updatedAt: '2026-06-30T00:00:00.000Z'
        }, 201)
      }
      if (String(input).endsWith('/api/guilds/guild-discord-clone/message-reports/report-backend-1/resolve')) {
        return jsonResponse({
          id: 'report-backend-1',
          channelId: 'channel-architecture',
          messageId: 'message-architecture-notes',
          reporterId: 'user-backend',
          reason: 'MODERATOR_REVIEW',
          status: 'RESOLVED',
          moderatorId: 'moderator-backend',
          resolution: 'resolved',
          createdAt: '2026-06-30T00:00:00.000Z',
          updatedAt: '2026-06-30T00:01:00.000Z'
        })
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })

    await expect(shell.reportMessage('message-architecture-notes', 'access-token')).resolves.toBe(true)
    expect(shell.openMessageReports[0]?.id).toBe('report-backend-1')
    expect((calls[0]?.init?.headers as Record<string, string>).Authorization).toBe('Bearer access-token')
    expect(JSON.parse(String(calls[0]?.init?.body))).toEqual({ reason: 'MODERATOR_REVIEW' })

    await expect(shell.resolveMessageReport('report-backend-1', 'RESOLVED', 'access-token')).resolves.toBe(true)
    expect(shell.openMessageReports).toHaveLength(0)
    expect(shell.moderation.messageReports[0]?.moderator).toBe('moderator-backend')
  })

  it('sends composed messages from the active channel composer', async () => {
    const wrapper = await mountSuspended(App)
    const input = wrapper.get('[data-testid="message-input"]')

    const beforeEmptySubmit = wrapper.findAll('[data-testid="message-card"]').length
    await input.setValue('   ')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')
    expect(wrapper.findAll('[data-testid="message-card"]')).toHaveLength(beforeEmptySubmit)

    await input.setValue('Shipping T04 from the composer')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Shipping T04 from the composer')
    expect((input.element as HTMLInputElement).value).toBe('')
  })

  it('applies real-backend guild, channel, message, voice, and stage responses after API success', async () => {
    await mountSuspended(App)
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      const body = init?.body ? JSON.parse(String(init.body)) : {}
      if (path.endsWith('/api/guilds')) {
        return jsonResponse({ id: 'guild-live', name: body.name, ownerId: 'user-1' })
      }
      if (path.endsWith('/api/guilds/guild-live/channels') && body.type === 'GUILD_TEXT') {
        return jsonResponse({ id: 'channel-live-text', name: body.name, type: 'GUILD_TEXT', parentId: null })
      }
      if (path.endsWith('/api/guilds/guild-live/channels') && body.type === 'GUILD_VOICE') {
        return jsonResponse({ id: 'channel-live-voice', name: body.name, type: 'GUILD_VOICE', parentId: null })
      }
      if (path.endsWith('/api/channels/channel-live-text/messages')) {
        return jsonResponse({
          id: 'message-live',
          guildId: 'guild-live',
          channelId: 'channel-live-text',
          authorId: 'user-1',
          content: body.content,
          mentions: ['user-1'],
          pinned: false,
          deleted: false,
          edited: false,
          editHistory: [],
          createdAt: '2026-05-14T00:00:00.000Z',
          updatedAt: '2026-05-14T00:00:00.000Z'
        })
      }
      if (path.endsWith('/api/voice/channels/channel-live-voice/join')) {
        return jsonResponse({
          participant: {
            userId: 'user-1',
            channelId: 'channel-live-voice',
            muted: false,
            deafened: false,
            speaking: false,
            screenSharing: false
          },
          token: { provider: 'LIVEKIT_SKELETON', token: 'voice-token' }
        })
      }
      if (path.endsWith('/api/stage/channels/channel-live-voice/sessions')) {
        return jsonResponse({
          id: 'stage-live',
          guildId: 'guild-live',
          channelId: 'channel-live-voice',
          topic: body.topic,
          moderatorIds: ['user-1'],
          speakerIds: [],
          audienceIds: [],
          pendingSpeakerIds: []
        })
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })
    const shell = useShellStore()
    shell.$reset()

    const guild = await shell.createBackendGuild('Live Guild', 'access-token')
    const text = await shell.createBackendChannel(guild!.id, 'live-text', 'GUILD_TEXT', 'access-token')
    const voice = await shell.createBackendChannel(guild!.id, 'live-voice', 'GUILD_VOICE', 'access-token')
    const message = await shell.sendBackendMessage(text!.id, 'backend hello', 'access-token')
    await shell.joinBackendVoice(voice!.id, 'access-token')
    await shell.startBackendStage(voice!.id, 'Backend stage', 'access-token')

    expect(shell.guild).toEqual({ id: 'guild-live', name: 'Live Guild' })
    expect(shell.activeChannelId).toBe('channel-live-text')
    expect(shell.messages.find((candidate) => candidate.id === message?.id)?.body).toBe('backend hello')
    expect(shell.voice.token).toBe('voice-token')
    expect(shell.experience.stageSession?.topic).toBe('Backend stage')
    expect(calls.every((call) => (call.init?.headers as Record<string, string>).Authorization === 'Bearer access-token')).toBe(true)
    expect(calls.every((call) => (call.init?.headers as Record<string, string>)['X-Request-Id']?.startsWith('web-shell-'))).toBe(true)
    expect(shell.apiLastRequestId).toMatch(/^web-shell-[a-z0-9-]+$/)
    expect(shell.apiError).toBeNull()
  })

  it('hydrates the user workspace from authenticated guild bootstrap response', async () => {
    const wrapper = await mountSuspended(App)
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      if (path.endsWith('/api/users/@me/guilds')) {
        return jsonResponse({
          guilds: [
            {
              id: 'guild-user-home',
              name: 'User Home',
              ownerId: 'user-1',
              channels: [
                { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null },
                { id: 'channel-design', name: 'design', type: 'GUILD_TEXT', parentId: null },
                { id: 'channel-standup', name: 'standup', type: 'GUILD_VOICE', parentId: null }
              ]
            }
          ]
        })
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })
    const shell = useShellStore()
    const preferences = usePreferencesStore()
    shell.$reset()
    preferences.setLocale('en-US')

    const guilds = await shell.loadCurrentUserGuilds('access-token')

    expect(guilds).toHaveLength(1)
    expect(shell.guild).toEqual({ id: 'guild-user-home', name: 'User Home' })
    expect(shell.activeChannelId).toBe('channel-ops')
    expect(shell.channelGroups.find((group) => group.id === 'text-channels')?.channels).toEqual([
      { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT' },
      { id: 'channel-design', name: 'design', type: 'GUILD_TEXT' }
    ])
    expect(shell.channelGroups.find((group) => group.id === 'voice-channels')?.channels).toEqual([
      { id: 'channel-standup', name: 'standup', type: 'GUILD_VOICE' }
    ])
    await nextTick()
    expect(wrapper.get('[data-testid="message-empty-state"]').text()).toContain('No messages yet')
    expect(wrapper.get('[data-testid="message-empty-state"]').text()).toContain('# ops')
    expect(calls).toHaveLength(1)
    expect(String(calls[0].input)).toContain('/api/users/@me/guilds')
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer access-token')
    expect(shell.apiError).toBeNull()
  })

  it('loads active channel messages after authenticated shell bootstrap', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      if (path.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: 'access-token',
          user: { id: 'user-1', username: 'user', displayName: 'User' }
        })
      }
      if (path.endsWith('/api/users/@me/guilds')) {
        return jsonResponse({
          guilds: [
            {
              id: 'guild-user-home',
              name: 'User Home',
              ownerId: 'user-1',
              channels: [
                { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null },
                { id: 'channel-standup', name: 'standup', type: 'GUILD_VOICE', parentId: null }
              ]
            }
          ]
        })
      }
      if (path.includes('/api/channels/channel-ops/messages')) {
        return jsonResponse({
          messages: [
            {
              id: 'message-server-backed',
              channelId: 'channel-ops',
              authorId: 'user-1',
              content: 'Server-backed hello from ops',
              mentions: [],
              pinned: false,
              deleted: false,
              edited: false,
              createdAt: '2026-05-22T07:30:00.000Z'
            }
          ],
          nextCursor: null
        })
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })

    const wrapper = await mountSuspended(App)

    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Server-backed hello from ops')
    })
    expect(wrapper.find('[data-testid="message-empty-state"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="unread-badge-channel-ops"]').exists()).toBe(false)
    expect(calls.some((call) => String(call.input).includes('/api/channels/channel-ops/messages'))).toBe(true)
    expect(
      calls
        .filter((call) => String(call.input).includes('/api/channels/channel-ops/messages'))
        .every((call) => (call.init?.headers as Record<string, string>).Authorization === 'Bearer access-token')
    ).toBe(true)
  })

  it('loads older active channel messages with the backend cursor', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      if (path.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: 'access-token',
          user: { id: 'user-1', username: 'user', displayName: 'User' }
        })
      }
      if (path.endsWith('/api/users/@me/guilds')) {
        return jsonResponse({
          guilds: [
            {
              id: 'guild-user-home',
              name: 'User Home',
              ownerId: 'user-1',
              channels: [
                { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null }
              ]
            }
          ]
        })
      }
      if (path.includes('/api/channels/channel-ops/messages') && path.includes('before=cursor-oldest-loaded')) {
        return jsonResponse({
          messages: [
            {
              id: 'message-older',
              channelId: 'channel-ops',
              authorId: 'user-2',
              content: 'Older server-backed context',
              mentions: [],
              pinned: false,
              deleted: false,
              edited: false,
              createdAt: '2026-05-22T07:00:00.000Z'
            }
          ],
          nextCursor: null
        })
      }
      if (path.includes('/api/channels/channel-ops/messages')) {
        return jsonResponse({
          messages: [
            {
              id: 'message-newer',
              channelId: 'channel-ops',
              authorId: 'user-1',
              content: 'Newest server-backed context',
              mentions: [],
              pinned: false,
              deleted: false,
              edited: false,
              createdAt: '2026-05-22T07:30:00.000Z'
            }
          ],
          nextCursor: 'cursor-oldest-loaded'
        })
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })

    const wrapper = await mountSuspended(App)

    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Newest server-backed context')
    })
    expect(wrapper.get('[data-testid="load-older-messages"]').text()).toContain('Load older messages')

    await wrapper.get('[data-testid="load-older-messages"]').trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Older server-backed context')
    })
    const messageTexts = wrapper.findAll('[data-testid="message-card"]').map((message) => message.text())
    expect(messageTexts[0]).toContain('Older server-backed context')
    expect(messageTexts.at(-1)).toContain('Newest server-backed context')
    expect(wrapper.find('[data-testid="load-older-messages"]').exists()).toBe(false)
    const olderCall = calls.find((call) => String(call.input).includes('before=cursor-oldest-loaded'))
    expect(olderCall).toBeDefined()
    expect((olderCall!.init?.headers as Record<string, string>).Authorization).toBe('Bearer access-token')
  })

  it('sends composer text through the authenticated backend message workflow', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      if (path.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: 'access-token',
          user: { id: 'user-1', username: 'user', displayName: 'User' }
        })
      }
      if (path.endsWith('/api/users/@me/guilds')) {
        return jsonResponse({
          guilds: [
            {
              id: 'guild-user-home',
              name: 'User Home',
              ownerId: 'user-1',
              channels: [
                { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null },
                { id: 'channel-standup', name: 'standup', type: 'GUILD_VOICE', parentId: null }
              ]
            }
          ]
        })
      }
      if (path.includes('/api/channels/channel-ops/messages') && init?.method === 'GET') {
        return jsonResponse({ messages: [], nextCursor: null })
      }
      if (path.endsWith('/api/channels/channel-ops/messages') && init?.method === 'POST') {
        const body = JSON.parse(String(init.body))
        return jsonResponse({
          id: 'message-authenticated-send',
          channelId: 'channel-ops',
          authorId: 'user-1',
          content: body.content,
          mentions: ['cto-bot'],
          pinned: false,
          deleted: false,
          edited: false,
          createdAt: '2026-05-22T08:30:00.000Z'
        }, 201)
      }
      return jsonResponse({ message: 'unexpected path' }, 404)
    })

    const wrapper = await mountSuspended(App)
    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="editor-titlebar"]').text()).toContain('# ops')
    })

    await wrapper.get('[data-testid="message-input"]').setValue('Authenticated hello @cto-bot')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Authenticated hello @cto-bot')
    })

    const messagePost = calls.find(
      (call) => String(call.input).endsWith('/api/channels/channel-ops/messages') && call.init?.method === 'POST'
    )
    expect(messagePost).toBeDefined()
    expect((messagePost!.init?.headers as Record<string, string>).Authorization).toBe('Bearer access-token')
    expect((messagePost!.init?.headers as Record<string, string>)['X-Request-Id']).toMatch(/^web-shell-/)
    expect(JSON.parse(String(messagePost!.init?.body))).toMatchObject({
      content: 'Authenticated hello @cto-bot'
    })
    expect(JSON.parse(String(messagePost!.init?.body)).clientEventId).toMatch(/^web-shell:/)
    expect(JSON.parse(String(messagePost!.init?.body)).idempotencyKey)
      .toBe(JSON.parse(String(messagePost!.init?.body)).clientEventId)
    await vi.waitFor(() => {
      expect((wrapper.get('[data-testid="message-input"]').element as HTMLInputElement).value).toBe('')
    })
    expect(useShellStore().pendingMutations).toHaveLength(0)
    expect(useShellStore().failedMutations).toHaveLength(0)
  })

  it('uploads and attaches staged composer images through the authenticated backend workflow', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      const path = String(input)
      if (path.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: 'access-token',
          user: { id: 'user-1', username: 'user', displayName: 'User' }
        })
      }
      if (path.endsWith('/api/users/@me/guilds')) {
        return jsonResponse({
          guilds: [
            {
              id: 'guild-user-home',
              name: 'User Home',
              ownerId: 'user-1',
              channels: [
                { id: 'channel-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null }
              ]
            }
          ]
        })
      }
      if (path.includes('/api/channels/channel-ops/messages') && init?.method === 'GET') {
        return jsonResponse({ messages: [], nextCursor: null })
      }
      if (path.endsWith('/api/attachments/uploads') && init?.method === 'POST') {
        return jsonResponse({
          attachmentId: 'attachment-live',
          objectKey: 'guild-user-home/channel-ops/attachment-live/qa-snapshot.png',
          uploadUrl: 'https://uploads.example.test/attachment-live'
        }, 201)
      }
      if (path === 'https://uploads.example.test/attachment-live' && init?.method === 'PUT') {
        return new Response(null, { status: 200 })
      }
      if (path.endsWith('/api/attachments/attachment-live/uploaded') && init?.method === 'PUT') {
        return jsonResponse({
          id: 'attachment-live',
          guildId: 'guild-user-home',
          channelId: 'channel-ops',
          ownerId: 'user-1',
          messageId: null,
          filename: 'qa-snapshot.png',
          contentType: 'image/png',
          sizeBytes: 1234,
          objectKey: 'guild-user-home/channel-ops/attachment-live/qa-snapshot.png',
          status: 'UPLOADED',
          createdAt: '2026-05-22T08:30:00.000Z',
          updatedAt: '2026-05-22T08:30:00.000Z'
        })
      }
      if (path.endsWith('/api/channels/channel-ops/messages') && init?.method === 'POST') {
        const body = JSON.parse(String(init.body))
        return jsonResponse({
          id: 'message-with-attachment',
          channelId: 'channel-ops',
          authorId: 'user-1',
          content: body.content,
          mentions: [],
          pinned: false,
          deleted: false,
          edited: false,
          createdAt: '2026-05-22T08:31:00.000Z'
        }, 201)
      }
      if (path.endsWith('/api/channels/channel-ops/messages/message-with-attachment/attachments/attachment-live') && init?.method === 'POST') {
        return jsonResponse({
          id: 'attachment-live',
          guildId: 'guild-user-home',
          channelId: 'channel-ops',
          ownerId: 'user-1',
          messageId: 'message-with-attachment',
          filename: 'qa-snapshot.png',
          contentType: 'image/png',
          sizeBytes: 1234,
          objectKey: 'guild-user-home/channel-ops/attachment-live/qa-snapshot.png',
          status: 'ATTACHED',
          createdAt: '2026-05-22T08:30:00.000Z',
          updatedAt: '2026-05-22T08:31:00.000Z'
        })
      }
      return jsonResponse({ message: `unexpected path ${path}` }, 404)
    })

    const wrapper = await mountSuspended(App)
    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="editor-titlebar"]').text()).toContain('# ops')
    })

    await wrapper.get('[data-testid="attachment-stage-demo"]').trigger('click')
    await wrapper.get('[data-testid="message-input"]').setValue('Authenticated attachment')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    await vi.waitFor(() => {
      expect(wrapper.get('[data-testid="message-attachment-attachment-live"]').text()).toContain('qa-snapshot.png')
    })
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Authenticated attachment')
    expect(wrapper.find('[data-testid="attachment-preview"]').exists()).toBe(false)

    const uploadRequest = calls.find((call) => String(call.input).endsWith('/api/attachments/uploads'))
    expect(uploadRequest).toBeDefined()
    expect((uploadRequest!.init?.headers as Record<string, string>).Authorization).toBe('Bearer access-token')
    expect(JSON.parse(String(uploadRequest!.init?.body))).toMatchObject({
      channelId: 'channel-ops',
      filename: 'qa-snapshot.png',
      contentType: 'image/png',
      sizeBytes: 1234
    })
    expect(calls.some((call) => String(call.input) === 'https://uploads.example.test/attachment-live' && call.init?.method === 'PUT')).toBe(true)
    expect(calls.some((call) => String(call.input).endsWith('/api/attachments/attachment-live/uploaded') && call.init?.method === 'PUT')).toBe(true)
    expect(calls.some((call) => String(call.input).endsWith('/api/channels/channel-ops/messages/message-with-attachment/attachments/attachment-live') && call.init?.method === 'POST')).toBe(true)
  })

  it('does not mutate shell state when a real-backend action is rejected', async () => {
    await mountSuspended(App)
    vi.stubGlobal('fetch', async () => jsonResponse({ message: 'forbidden' }, 403))
    const shell = useShellStore()
    shell.$reset()
    const originalGuild = { ...shell.guild }

    await expect(shell.createBackendGuild('Rejected Guild', 'bad-token')).resolves.toBeNull()

    expect(shell.guild).toEqual(originalGuild)
    expect(shell.apiError).toContain('Discord API rejected the request')
    expect(shell.apiError).toContain(shell.apiLastRequestId)
  })

  it('stages a deterministic image attachment preview from the composer', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="attachment-stage-demo"]').trigger('click')

    const preview = wrapper.get('[data-testid="attachment-preview"]')
    expect(preview.text()).toContain('qa-snapshot.png')
    expect(preview.text()).toContain('image/png')
    expect(preview.text()).toContain('1.2 KB')
    expect(preview.get('[data-testid="attachment-preview-image"]').attributes('alt')).toBe('Preview qa-snapshot.png')
  })

  it('sends attachment metadata and clears the staged preview', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="attachment-stage-demo"]').trigger('click')
    await wrapper.get('[data-testid="message-input"]').setValue('Shipping attachment metadata')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    expect(wrapper.find('[data-testid="attachment-preview"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Shipping attachment metadata')
    expect(wrapper.get('[data-testid="message-attachment-attachment-demo-image"]').text()).toContain('qa-snapshot.png')
  })

  it('provides keyboard skip path and traps focus inside invite modal', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="skip-to-workspace"]').attributes('href')).toBe('#workspace-content')
    expect(wrapper.get('[data-testid="workspace"]').attributes('id')).toBe('workspace-content')
    expect(wrapper.get('[data-testid="workspace"]').attributes('tabindex')).toBe('-1')
    expect(wrapper.get('[data-testid="message-input"]').attributes('aria-label')).toBe('Message composer')
    expect(wrapper.get('[data-testid="attachment-stage-demo"]').text()).toContain('Attach image')

    const acceptButton = wrapper.get('[data-testid="invite-accept"]')
    const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    acceptButton.element.dispatchEvent(tabEvent)

    expect(tabEvent.defaultPrevented).toBe(true)
  })

  it('extracts user mentions without matching emails and scopes sends to the active channel', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')
    await wrapper.get('[data-testid="message-input"]').setValue('Ping dev@example.com @cto-bot @CTO-BOT')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Ping dev@example.com @cto-bot @CTO-BOT')
    expect(wrapper.findAll('[data-testid="mention-chip-cto-bot"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="mention-chip-example"]').exists()).toBe(false)

    await wrapper.get('[data-testid="channel-general"]').trigger('click')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).not.toContain('Ping dev@example.com @cto-bot @CTO-BOT')
  })

  it('renders role permissions, member assignments, and active channel overwrites', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="role-permission-panel"]').text()).toContain('Role permissions')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('MANAGE_MESSAGES')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('VIEW_CHANNEL')
    expect(wrapper.get('[data-testid="member-vibe-coder-roles"]').text()).toContain('vibe-coder')
    expect(wrapper.get('[data-testid="member-vibe-coder-roles"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('# general')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Allow SEND_MESSAGES')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Deny MANAGE_CHANNELS')
    expect(wrapper.get('[data-testid="workspace"]').find('[data-testid="role-permission-panel"]').exists()).toBe(true)
  })

  it('renders admin permission diff, preview-as-role, and privileged audit feedback', async () => {
    const wrapper = await mountSuspended(App)
    const rolePanel = wrapper.get('[data-testid="role-permission-panel"]')

    expect(rolePanel.get('[data-testid="permission-diff"]').text()).toContain('Moderator')
    expect(rolePanel.get('[data-testid="permission-diff"]').text()).toContain('MANAGE_CHANNELS')
    expect(rolePanel.get('[data-testid="permission-diff"]').text()).toContain('Before denied')
    expect(rolePanel.get('[data-testid="permission-diff"]').text()).toContain('After allowed')
    expect(rolePanel.get('[data-testid="preview-as-role"]').text()).toContain('Preview as Moderator')
    expect(rolePanel.get('[data-testid="preview-as-role"]').text()).toContain('Allowed SEND_MESSAGES')
    expect(rolePanel.get('[data-testid="preview-as-role"]').text()).toContain('Denied MANAGE_CHANNELS')

    await rolePanel.get('[data-testid="apply-permission-draft"]').trigger('click')

    expect(rolePanel.get('[data-testid="role-moderator"]').text()).toContain('MANAGE_CHANNELS')
    expect(rolePanel.get('[data-testid="privileged-audit"]').text()).toContain('ROLE_PERMISSION_UPDATED')
    expect(rolePanel.get('[data-testid="privileged-audit"]').text()).toContain('Moderator MANAGE_CHANNELS')
  })

  it('renders the invite preview modal with limits, role grants, and accept CTA inside the workspace', async () => {
    const wrapper = await mountSuspended(App)
    const inviteModal = wrapper.get('[data-testid="invite-modal"]')

    expect(wrapper.get('[data-testid="workspace"]').find('[data-testid="invite-modal"]').exists()).toBe(true)
    expect(inviteModal.attributes('role')).toBe('dialog')
    expect(inviteModal.attributes('aria-modal')).toBe('true')
    expect(inviteModal.attributes('tabindex')).toBe('-1')
    expect(inviteModal.text()).toContain('Join Discord Clone')
    expect(inviteModal.get('[data-testid="invite-preview"]').text()).toContain('Previewing # general')
    expect(inviteModal.get('[data-testid="invite-expiry"]').text()).toContain('Expires in 7 days')
    expect(inviteModal.get('[data-testid="invite-max-uses"]').text()).toContain('12 uses remaining')
    expect(inviteModal.get('[data-testid="invite-role-grants"]').text()).toContain('Role grants')
    expect(inviteModal.get('[data-testid="invite-role-grants"]').text()).toContain('Moderator')
    expect(inviteModal.get('[data-testid="invite-accept"]').text()).toContain('Accept invite')
  })

  it('selects channels with accessible channel buttons and shows channel-specific messages', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')

    expect(wrapper.get('[data-testid="active-channel"]').text()).toContain('# architecture')
    expect(wrapper.get('[data-channel-id="channel-architecture"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Architecture notes belong in this channel')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).not.toContain('Welcome to the guild')
  })

  it('renders deterministic gateway READY, heartbeat ACK, resume, and last sequence state', async () => {
    const wrapper = await mountSuspended(App)
    const gatewayPanel = wrapper.get('[data-testid="gateway-status-panel"]')

    expect(gatewayPanel.text()).toContain('Gateway')
    expect(gatewayPanel.get('[data-testid="gateway-status"]').text()).toContain('READY')
    expect(gatewayPanel.get('[data-testid="gateway-last-sequence"]').text()).toContain('Last sequence 42')
    expect(gatewayPanel.get('[data-testid="gateway-heartbeat-ack"]').text()).toContain('Heartbeat ACK received')
    expect(gatewayPanel.get('[data-testid="gateway-resumed"]').text()).toContain('Session resumed')
    expect(gatewayPanel.get('[data-testid="gateway-event-42"]').text()).toContain('READY')
  })

  it('guards duplicate gateway events by sequence number', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()

    ;(shell as any).recordGatewayEvent?.({
      sequence: 41,
      type: 'MESSAGE_DELETE',
      label: 'stale delete'
    })
    ;(shell as any).recordGatewayEvent?.({
      sequence: 43,
      type: 'MESSAGE_CREATE',
      label: 'message created'
    })
    ;(shell as any).recordGatewayEvent?.({
      sequence: 43,
      type: 'MESSAGE_UPDATE',
      label: 'duplicate message update'
    })
    await nextTick()

    expect(wrapper.find('[data-gateway-sequence="41"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-gateway-sequence="43"]')).toHaveLength(1)
    expect(wrapper.get('[data-gateway-sequence="43"]').text()).toContain('MESSAGE_CREATE')
    expect(wrapper.get('[data-gateway-sequence="43"]').text()).not.toContain('MESSAGE_UPDATE')
  })

  it('renders direct and group DM lists inside the workspace', async () => {
    const wrapper = await mountSuspended(App)
    const dmSidebar = wrapper.get('[data-testid="dm-sidebar"]')

    expect(wrapper.get('[data-testid="workspace"]').find('[data-testid="dm-sidebar"]').exists()).toBe(true)
    expect(dmSidebar.text()).toContain('Direct messages')
    expect(dmSidebar.get('[data-testid="dm-friend-cto-bot"]').text()).toContain('cto-bot')
    expect(dmSidebar.get('[data-testid="dm-friend-cto-bot"]').text()).toContain('Friend')
    expect(dmSidebar.get('[data-testid="group-dm-t07-strike-team"]').text()).toContain('T07 strike team')
    expect(dmSidebar.get('[data-testid="group-dm-t07-strike-team"]').attributes('aria-current')).toBe('page')
    expect(dmSidebar.get('[data-testid="active-dm-summary"]').text()).toContain('T07 strike team')
  })

  it('surfaces blocked users and prevents selecting their DM', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="dm-friend-spam-drone"]').trigger('click')

    expect(wrapper.get('[data-testid="dm-blocked-spam-drone"]').text()).toContain('Blocked')
    expect(wrapper.get('[data-testid="active-dm-summary"]').text()).not.toContain('spam-drone')
    expect(wrapper.get('[data-testid="active-dm-summary"]').text()).toContain('T07 strike team')
  })

  it('adds and removes group DM members through store-backed shell actions', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()

    ;(shell as any).addGroupDmMember?.('group-dm-t07-strike-team', 'qa-scout')
    await nextTick()

    expect(wrapper.get('[data-testid="group-dm-members"]').text()).toContain('qa-scout')
    expect(wrapper.get('[data-testid="group-dm-member-qa-scout"]').text()).toContain('qa-scout')

    await wrapper.get('[data-testid="remove-group-member-qa-scout"]').trigger('click')

    expect(wrapper.find('[data-testid="group-dm-member-qa-scout"]').exists()).toBe(false)
  })

  it('renders a group call skeleton and toggles participant state', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="group-call-skeleton"]').text()).toContain('Group call')
    expect(wrapper.get('[data-testid="group-call-status"]').text()).toContain('Call idle')

    await wrapper.get('[data-testid="group-call-toggle"]').trigger('click')

    expect(wrapper.get('[data-testid="group-call-status"]').text()).toContain('Call active')
    expect(wrapper.get('[data-testid="group-call-participants"]').text()).toContain('vibe-coder')
  })

  it('renders reaction counts from unique reactors and toggles the current user reaction', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()

    ;(shell as any).addReaction?.('message-general-welcome', 'wave', 'vibe-coder')
    ;(shell as any).addReaction?.('message-general-welcome', 'wave', 'vibe-coder')
    await nextTick()

    const reaction = wrapper.get('[data-testid="reaction-chip-message-general-welcome-wave"]')
    expect(reaction.text()).toContain('Wave')
    expect(reaction.text()).toContain('1')
    expect(reaction.attributes('aria-pressed')).toBe('true')

    await reaction.trigger('click')

    expect(wrapper.find('[data-testid="reaction-chip-message-general-welcome-wave"]').exists()).toBe(false)
  })

  it('opens the expression panel and adds a selected expression to the message reactions', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    await wrapper.get('[data-testid="expression-toggle-message-general-welcome"]').trigger('click')

    const panel = wrapper.get('[data-testid="expression-panel-message-general-welcome"]')
    expect(panel.attributes('role')).toBe('dialog')
    expect(panel.text()).toContain('Expressions')
    expect(panel.text()).toContain('Ship It')
    expect(panel.text()).toContain('Approved sticker')

    await panel.get('[data-testid="expression-option-message-general-welcome-shipit"]').trigger('click')

    const reaction = wrapper.get('[data-testid="reaction-chip-message-general-welcome-shipit"]')
    expect(reaction.text()).toContain('Ship It')
    expect(reaction.text()).toContain('1')
    expect(wrapper.find('[data-testid="expression-panel-message-general-welcome"]').exists()).toBe(false)
  })

  it('renders forum guidelines, tags, public/private threads, and archive state', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    const forumPanel = wrapper.get('[data-testid="forum-panel"]')
    expect(forumPanel.text()).toContain('Forum')
    expect(forumPanel.get('[data-testid="forum-guidelines"]').text()).toContain('Use tags before posting')
    expect(forumPanel.get('[data-testid="forum-tag-release"]').text()).toContain('release')
    expect(forumPanel.get('[data-testid="forum-tag-help"]').text()).toContain('help')
    expect(forumPanel.get('[data-testid="thread-public-release-notes"]').text()).toContain('Public')
    expect(forumPanel.get('[data-testid="thread-private-mod-review"]').text()).toContain('Private')
    expect(forumPanel.get('[data-testid="thread-status-thread-archived-incident"]').text()).toContain('Archived')
  })

  it('prevents archived thread writes until reopened', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    await wrapper.get('[data-testid="thread-archived-incident"]').trigger('click')
    await wrapper.get('[data-testid="thread-write-thread-archived-incident"]').trigger('click')

    expect(wrapper.get('[data-testid="thread-write-receipt"]').text()).toContain('Thread is archived')

    await wrapper.get('[data-testid="reopen-thread-thread-archived-incident"]').trigger('click')
    await wrapper.get('[data-testid="thread-write-thread-archived-incident"]').trigger('click')

    expect(wrapper.get('[data-testid="thread-status-thread-archived-incident"]').text()).toContain('Open')
    expect(wrapper.get('[data-testid="thread-write-receipt"]').text()).toContain('Thread write accepted')
  })

  it('requires a forum tag before creating a forum post', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()
    await nextTick()

    await wrapper.get('[data-testid="create-forum-post-without-tag"]').trigger('click')

    expect(wrapper.get('[data-testid="forum-post-error"]').text()).toContain('Select at least one tag')

    await wrapper.get('[data-testid="create-forum-post-release"]').trigger('click')

    expect(wrapper.get('[data-testid="forum-post-error"]').text()).toContain('Ready')
    expect(wrapper.get('[data-testid="thread-forum-release-plan"]').text()).toContain('Release plan')
  })

  it('renders moderation onboarding, automod, and audit state', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    const moderationPanel = wrapper.get('[data-testid="moderation-panel"]')
    expect(moderationPanel.text()).toContain('Moderation')
    expect(moderationPanel.get('[data-testid="onboarding-question"]').text()).toContain('Choose your squad')
    expect(moderationPanel.get('[data-testid="automod-rule-keyword-leak"]').text()).toContain('leak')
    expect(moderationPanel.get('[data-testid="audit-log"]').text()).toContain('AUDIT_RULE_CREATED')
  })

  it('submits onboarding answers, blocks AutoMod content, and appends audit entries', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    await wrapper.get('[data-testid="submit-onboarding-answer-engineering"]').trigger('click')

    expect(wrapper.get('[data-testid="onboarding-assigned-role"]').text()).toContain('Engineering')
    expect(wrapper.get('[data-testid="audit-log"]').text()).toContain('ONBOARDING_ROLE_ASSIGNED')

    await wrapper.get('[data-testid="simulate-automod-block"]').trigger('click')

    expect(wrapper.get('[data-testid="automod-decision"]').text()).toContain('Blocked before persist')
    expect(wrapper.get('[data-testid="audit-log"]').text()).toContain('AUTOMOD_MESSAGE_BLOCKED')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).not.toContain('leak the release token')
  })

  it('renders voice token provider, participants, controls, and event records', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    const voicePanel = wrapper.get('[data-testid="voice-panel"]')
    expect(voicePanel.text()).toContain('Voice')
    expect(voicePanel.get('[data-testid="voice-token-provider"]').text()).toContain('LIVEKIT_SKELETON')
    expect(voicePanel.get('[data-testid="voice-participants"]').text()).toContain('No participants')
    expect(voicePanel.get('[data-testid="voice-events"]').text()).toContain('VOICE_READY')
  })

  it('joins voice, toggles local voice state, screen shares, and leaves cleanly', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    await wrapper.get('[data-testid="voice-join-channel-war-room"]').trigger('click')

    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('Voice connected: war-room')
    expect(wrapper.get('[data-testid="voice-participants"]').text()).toContain('vibe-coder')
    expect(wrapper.get('[data-testid="voice-events"]').text()).toContain('VOICE_JOIN')

    await wrapper.get('[data-testid="voice-toggle-mute"]').trigger('click')
    await wrapper.get('[data-testid="voice-toggle-deaf"]').trigger('click')
    await wrapper.get('[data-testid="voice-toggle-speaking"]').trigger('click')
    await wrapper.get('[data-testid="voice-toggle-screen-share"]').trigger('click')

    expect(wrapper.get('[data-testid="voice-local-state"]').text()).toContain('Muted')
    expect(wrapper.get('[data-testid="voice-local-state"]').text()).toContain('Deafened')
    expect(wrapper.get('[data-testid="voice-local-state"]').text()).toContain('Speaking')
    expect(wrapper.get('[data-testid="voice-local-state"]').text()).toContain('Screen sharing')

    await wrapper.get('[data-testid="voice-leave"]').trigger('click')

    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('voice disconnected')
    expect(wrapper.get('[data-testid="voice-participants"]').text()).toContain('No participants')
    expect(wrapper.get('[data-testid="voice-events"]').text()).toContain('VOICE_LEAVE')
  })

  it('runs stage, soundboard, and premium skeleton flows', async () => {
    const wrapper = await mountSuspended(App)
    useShellStore().$reset()
    await nextTick()

    const experiencePanel = wrapper.get('[data-testid="experience-panel"]')
    expect(experiencePanel.text()).toContain('Experience')
    expect(experiencePanel.get('[data-testid="stage-topic"]').text()).toContain('No active stage')
    expect(experiencePanel.get('[data-testid="premium-gate"]').text()).toContain('Locked')

    await wrapper.get('[data-testid="stage-start"]').trigger('click')
    await wrapper.get('[data-testid="stage-request-speak"]').trigger('click')

    expect(wrapper.get('[data-testid="stage-topic"]').text()).toContain('T14 roadmap live review')
    expect(wrapper.get('[data-testid="stage-pending"]').text()).toContain('vibe-coder')
    expect(wrapper.get('[data-testid="stage-speakers"]').text()).toContain('No speakers')

    await wrapper.get('[data-testid="stage-approve-vibe-coder"]').trigger('click')

    expect(wrapper.get('[data-testid="stage-speakers"]').text()).toContain('vibe-coder')
    expect(wrapper.get('[data-testid="stage-pending"]').text()).toContain('No pending requests')

    await wrapper.get('[data-testid="stage-move-audience-vibe-coder"]').trigger('click')

    expect(wrapper.get('[data-testid="stage-speakers"]').text()).toContain('No speakers')
    expect(wrapper.get('[data-testid="stage-audience"]').text()).toContain('vibe-coder')

    await wrapper.get('[data-testid="soundboard-create-applause"]').trigger('click')
    await wrapper.get('[data-testid="soundboard-play-applause"]').trigger('click')

    expect(wrapper.get('[data-testid="soundboard-sounds"]').text()).toContain('Applause')
    expect(wrapper.get('[data-testid="soundboard-last-event"]').text()).toContain('played Applause in war-room')

    await wrapper.get('[data-testid="premium-check-hd-stream"]').trigger('click')
    expect(wrapper.get('[data-testid="premium-gate"]').text()).toContain('Locked')

    await wrapper.get('[data-testid="premium-grant-hd-stream"]').trigger('click')
    await wrapper.get('[data-testid="premium-check-hd-stream"]').trigger('click')

    expect(wrapper.get('[data-testid="premium-gate"]').text()).toContain('Unlocked')
    expect(wrapper.get('[data-testid="premium-catalog"]').text()).toContain('HD Stream Pack')
    expect(wrapper.get('[data-testid="premium-quests"]').text()).toContain('Stream for 10 minutes')
  })
})
