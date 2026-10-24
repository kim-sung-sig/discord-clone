import { expect, test } from '@playwright/test'

test('loads Discord shell landmarks', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('server-rail')).toContainText('Discord Clone')
  await expect(page.getByTestId('guild-name')).toContainText('Discord Clone')
  await expect(page.getByTestId('channel-general')).toContainText('#')
  await expect(page.getByTestId('channel-general')).toContainText('general')
  await expect(page.getByTestId('channel-war-room')).toContainText('Voice')
  await expect(page.getByTestId('channel-war-room')).toContainText('war-room')
  await expect(page.getByTestId('active-channel')).toContainText('# general')
  await expect(page.locator('[data-channel-id="channel-general"]')).toHaveAttribute('aria-current', 'page')
  await expect(page.getByTestId('chat-viewport')).toContainText('Welcome to the guild')
  await expect(page.getByTestId('message-pinned-label')).toContainText('Pinned')
  await expect(page.getByTestId('message-edited-marker')).toContainText('edited')
  await expect(page.getByTestId('message-tombstone')).toContainText('message deleted')
  await expect(page.getByTestId('mention-chip-cto-bot')).toContainText('@cto-bot')
  await expect(page.getByTestId('message-input')).toHaveAttribute('placeholder', /Message # general/)
  await expect(page.getByTestId('message-list')).toBeVisible()
  await expect(page.getByTestId('member-sidebar')).toContainText('Members')
  await expect(page.getByTestId('member-sidebar')).toContainText('online')
  await expect(page.getByTestId('user-panel')).toContainText('vibe-coder')
  await expect(page.getByTestId('gateway-status-panel')).toContainText('Gateway')
  await expect(page.getByTestId('gateway-status')).toContainText('READY')
  await expect(page.getByTestId('gateway-last-sequence')).toContainText('Last sequence 42')
  await expect(page.getByTestId('gateway-heartbeat-ack')).toContainText('Heartbeat ACK received')
  await expect(page.getByTestId('gateway-resumed')).toContainText('Session resumed')
  await expect(page.locator('[data-gateway-sequence="42"]')).toHaveCount(1)
})

test('sends a message from the active channel composer', async ({ page }) => {
  await page.goto('/')

  const messageCountBeforeEmptySubmit = await page.getByTestId('message-card').count()
  await page.getByTestId('message-input').fill('   ')
  await page.getByTestId('message-send').click()
  await expect(page.getByTestId('message-card')).toHaveCount(messageCountBeforeEmptySubmit)

  await page.getByTestId('message-input').fill('Playwright composer smoke message')
  await page.getByTestId('message-send').click()

  await expect(page.getByTestId('chat-viewport')).toContainText('Playwright composer smoke message')
  await expect(page.getByTestId('message-input')).toHaveValue('')
})

test('stages and sends a deterministic image attachment from the composer', async ({ page }) => {
  await page.goto('/')

  await page.getByTestId('attachment-stage-demo').click()
  await expect(page.getByTestId('attachment-preview')).toContainText('qa-snapshot.png')
  await expect(page.getByTestId('attachment-preview')).toContainText('1.2 KB')

  await page.getByTestId('message-input').fill('Playwright attachment smoke')
  await page.getByTestId('message-send').click()

  await expect(page.getByTestId('attachment-preview')).toHaveCount(0)
  await expect(page.getByTestId('chat-viewport')).toContainText('Playwright attachment smoke')
  await expect(page.getByTestId('message-attachment-attachment-demo-image')).toContainText('qa-snapshot.png')
})

test('selects a text channel and updates active channel content', async ({ page }) => {
  await page.goto('/')

  await page.getByTestId('channel-architecture').click()

  await expect(page.getByTestId('active-channel')).toContainText('# architecture')
  await expect(page.locator('[data-channel-id="channel-architecture"]')).toHaveAttribute('aria-current', 'page')
  await expect(page.getByTestId('chat-viewport')).toContainText('Architecture notes belong in this channel')
  await expect(page.getByTestId('chat-viewport')).not.toContainText('Welcome to the guild')

  await page.getByTestId('message-input').fill('Scoped architecture note @cto-bot @CTO-BOT dev@example.com')
  await page.getByTestId('message-send').click()

  await expect(page.getByTestId('chat-viewport')).toContainText('Scoped architecture note')
  await expect(page.getByTestId('mention-chip-cto-bot')).toHaveCount(1)
  await expect(page.getByTestId('mention-chip-example')).toHaveCount(0)

  await page.getByTestId('channel-general').click()

  await expect(page.getByTestId('chat-viewport')).not.toContainText('Scoped architecture note')
})

test('clears a channel unread badge after selecting the channel', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('unread-badge-channel-architecture')).toContainText('1')

  await page.getByTestId('channel-architecture').click()

  await expect(page.getByTestId('unread-badge-channel-architecture')).toHaveCount(0)
})

test('opens notification inbox and jumps to unread channel work', async ({ page }) => {
  let lastReadSequence = 0
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: 'inbox-e2e-access-token',
        user: { id: 'viewer-id', username: 'viewer', displayName: 'Viewer' } })
    })
  })
  await page.route('**/api/users/@me/guilds', async (route) => {
    await route.fulfill({ json: { guilds: [{ id: 'guild-e2e', name: 'Inbox guild', ownerId: 'viewer-id',
      channels: ['general', 'architecture'].map((name) => ({
        id: `channel-${name}`, name, type: 'GUILD_TEXT', parentId: null
      })) }] } })
  })
  await page.route('**/api/channels/*/messages**', async (route) => {
    const architecture = new URL(route.request().url()).pathname.includes('channel-architecture')
    await route.fulfill({ json: { messages: architecture ? [{ id: 'message-architecture-notes',
      channelId: 'channel-architecture', sequence: 1, authorId: 'author-id', content: 'Architecture notes',
      mentions: ['viewer-id'], pinned: false, deleted: false, edited: false,
      createdAt: '2026-10-05T10:00:00Z', updatedAt: '2026-10-05T10:00:00Z' }] : [], nextCursor: null } })
  })
  await page.route('**/api/presence/channels/*/read', async (route) => {
    if (route.request().method() === 'PUT') lastReadSequence = route.request().postDataJSON().lastReadSequence
    await route.fulfill({ json: { lastReadSequence } })
  })
  await page.route('**/api/notifications**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/unread-count') ? { unreadCount: lastReadSequence ? 1 : 2 }
      : path.endsWith('/channel-unread') ? { 'channel-architecture': lastReadSequence ? 0 : 1 }
        : [{ id: 'mention-e2e', guildId: 'guild-e2e', channelId: 'channel-architecture',
          sourceId: 'message-architecture-notes', sequence: 1, kind: 'MENTION', summary: 'Architecture mention',
          read: lastReadSequence > 0 },
        { id: 'dm-e2e', channelId: 'dm-channel', sourceId: 'dm-message', sequence: 1,
          kind: 'DM', summary: 'New direct message', read: false, targetUserId: 'friend-id' }]
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('/')
  await expect(page.getByTestId('message-input')).toBeEnabled()

  await page.getByTestId('activity-inbox').click()

  await expect(page.getByTestId('workbench-inbox-view')).toContainText('Notification inbox')
  await expect(page.getByTestId('workbench-inbox-mention-message-architecture-notes')).toContainText('architecture')
  await expect(page.getByTestId('workbench-inbox-unread-channel-architecture')).toContainText('1')
  await expect(page.getByTestId('workbench-inbox-dm-dm-e2e')).toContainText('New direct message')

  await page.getByTestId('workbench-inbox-unread-channel-architecture').getByRole('button').click()

  await expect(page.getByTestId('workspace')).toHaveAttribute('data-active-workbench-view', 'explorer')
  await expect(page.getByTestId('active-channel')).toContainText('# architecture')
  await expect(page.getByTestId('unread-badge-channel-architecture')).toHaveCount(0)
})

test('searches messages and resolves a moderation report from the workbench', async ({ page }) => {
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: 'search-e2e-access-token' })
    })
  })
  await page.goto('/')
  await expect(page.getByTestId('message-input')).toBeEnabled()

  await page.getByTestId('activity-search').click()
  await page.getByTestId('workbench-search-input').fill('API contract')

  await expect(page.getByTestId('workbench-search-message-message-architecture-notes')).toContainText('API contract')
  await page.getByTestId('workbench-report-message-message-architecture-notes').click()

  const report = page.getByTestId('workbench-report-queue').locator('[data-testid^="workbench-report-"]').first()
  await expect(report).toContainText('Needs moderator review')
  await report.getByRole('button', { name: 'Resolve' }).click()

  await expect(page.getByTestId('workbench-report-queue')).not.toContainText('Needs moderator review')
})

test('shows active channel typing state in the message viewport', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('typing-indicator')).toContainText('cto-bot is typing')

  await page.getByTestId('channel-architecture').click()

  await expect(page.getByTestId('typing-indicator')).toHaveCount(0)
})

test('adds and removes a reaction from the first visible message', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByTestId('message-input')).toBeEnabled()

  await page.getByTestId('expression-toggle-message-general-welcome').click()
  await expect(page.getByTestId('expression-panel-message-general-welcome')).toContainText('Expressions')

  await page.getByTestId('expression-option-message-general-welcome-shipit').click()
  await expect(page.getByTestId('reaction-chip-message-general-welcome-shipit')).toContainText('1')

  await page.getByTestId('expression-toggle-message-general-welcome').click()
  await page.getByTestId('expression-option-message-general-welcome-shipit').click()
  await expect(page.getByTestId('reaction-chip-message-general-welcome-shipit')).toContainText('1')

  await page.getByTestId('reaction-chip-message-general-welcome-shipit').click()
  await expect(page.getByTestId('reaction-chip-message-general-welcome-shipit')).toHaveCount(0)
})

test('joins voice, toggles controls, screen shares, and leaves', async ({ page }) => {
  await page.goto('/')

  await page.getByTestId('activity-calls').click()
  const callsView = page.getByTestId('workbench-calls-view')
  await expect(callsView).toBeVisible()
  await expect(callsView.getByTestId('voice-panel')).toContainText('Voice')
  await expect(callsView.getByTestId('voice-token-provider')).toContainText('LIVEKIT_SKELETON')
  await expect(callsView.getByTestId('voice-participants')).toContainText('No participants')

  await callsView.getByTestId('voice-join-channel-war-room').click()
  await expect(page.getByTestId('user-panel')).toContainText('Voice connected: war-room')
  await expect(callsView.getByTestId('voice-participants')).toContainText('vibe-coder')
  await expect(callsView.getByTestId('voice-events')).toContainText('VOICE_JOIN')

  await callsView.getByTestId('voice-toggle-mute').click()
  await callsView.getByTestId('voice-toggle-deaf').click()
  await callsView.getByTestId('voice-toggle-speaking').click()
  await callsView.getByTestId('voice-toggle-screen-share').click()
  await expect(callsView.getByTestId('voice-local-state')).toContainText('Muted')
  await expect(callsView.getByTestId('voice-local-state')).toContainText('Deafened')
  await expect(callsView.getByTestId('voice-local-state')).toContainText('Speaking')
  await expect(callsView.getByTestId('voice-local-state')).toContainText('Screen sharing')

  await callsView.getByTestId('voice-leave').click()
  await expect(page.getByTestId('user-panel')).toContainText('voice disconnected')
  await expect(callsView.getByTestId('voice-participants')).toContainText('No participants')
  await expect(callsView.getByTestId('voice-events')).toContainText('VOICE_LEAVE')
})

test('keeps developer tooling and legacy contract panels out of the workspace', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('workspace')).toBeVisible()
  await expect(page.getByTestId('real-api-panel')).toHaveCount(0)
  await expect(page.getByTestId('legacy-shell-contracts')).toHaveCount(0)
  await expect(page.getByTestId('invite-modal')).toHaveCount(0)
  await expect(page.getByTestId('account-banner').getByRole('button')).toHaveCount(0)
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('uses Nuxt routing for unknown routes instead of rendering the app shell', async ({ page }) => {
  await page.goto('/definitely-not-a-real-route')

  await expect(page.getByTestId('server-rail')).toHaveCount(0)
  await expect(page.locator('body')).toContainText(/404|Page not found/i)
})
