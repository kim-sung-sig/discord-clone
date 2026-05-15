import { mountSuspended } from '@nuxt/test-utils/runtime'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../../app.vue'
import { useShellStore } from '../../stores/shell'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' }
  })

describe('Discord app shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
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
