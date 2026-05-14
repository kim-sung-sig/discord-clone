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
  await expect(page.getByTestId('role-permission-panel')).toContainText('Role permissions')
  await expect(page.getByTestId('role-moderator')).toContainText('Moderator')
  await expect(page.getByTestId('role-moderator')).toContainText('MANAGE_MESSAGES')
  await expect(page.getByTestId('member-vibe-coder-roles')).toContainText('vibe-coder')
  await expect(page.getByTestId('member-vibe-coder-roles')).toContainText('Moderator')
  await expect(page.getByTestId('active-channel-overwrite')).toContainText('# general')
  await expect(page.getByTestId('active-channel-overwrite')).toContainText('Allow SEND_MESSAGES')
  await expect(page.getByTestId('active-channel-overwrite')).toContainText('Deny MANAGE_CHANNELS')
  await expect(page.getByTestId('workspace').getByTestId('role-permission-panel')).toBeVisible()
  await expect(page.getByTestId('workspace').getByTestId('invite-modal')).toBeVisible()
  await expect(page.getByTestId('invite-modal')).toContainText('Join Discord Clone')
  await expect(page.getByTestId('invite-preview')).toContainText('Previewing # general')
  await expect(page.getByTestId('invite-expiry')).toContainText('Expires in 7 days')
  await expect(page.getByTestId('invite-max-uses')).toContainText('12 uses remaining')
  await expect(page.getByTestId('invite-role-grants')).toContainText('Role grants')
  await expect(page.getByTestId('invite-role-grants')).toContainText('Moderator')
  await expect(page.getByTestId('invite-accept')).toContainText('Accept invite')
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

test('shows active channel typing state in the message viewport', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('typing-indicator')).toContainText('cto-bot is typing')

  await page.getByTestId('channel-architecture').click()

  await expect(page.getByTestId('typing-indicator')).toHaveCount(0)
})

test('opens a group DM, mutates members, and starts the group call skeleton', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('dm-sidebar')).toContainText('Direct messages')
  await page.getByTestId('group-dm-t07-strike-team').click()

  await expect(page.getByTestId('active-dm-summary')).toContainText('T07 strike team')
  await expect(page.getByTestId('group-dm-members')).toContainText('vibe-coder')

  await page.getByTestId('add-group-member').click()
  await expect(page.getByTestId('group-dm-member-qa-scout')).toContainText('qa-scout')

  await page.getByTestId('remove-group-member-qa-scout').click()
  await expect(page.getByTestId('group-dm-member-qa-scout')).toHaveCount(0)

  await page.getByTestId('group-call-toggle').click()
  await expect(page.getByTestId('group-call-status')).toContainText('Call active')
  await expect(page.getByTestId('group-call-participants')).toContainText('vibe-coder')
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

test('manages forum tags and archived thread writes', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('forum-panel')).toContainText('Forum')
  await expect(page.getByTestId('forum-guidelines')).toContainText('Use tags before posting')
  await expect(page.getByTestId('thread-public-release-notes')).toContainText('Public')
  await expect(page.getByTestId('thread-private-mod-review')).toContainText('Private')
  await expect(page.getByTestId('thread-status-thread-archived-incident')).toContainText('Archived')

  await page.getByTestId('thread-archived-incident').click()
  await page.getByTestId('thread-write-thread-archived-incident').click()
  await expect(page.getByTestId('thread-write-receipt')).toContainText('Thread is archived')

  await page.getByTestId('reopen-thread-thread-archived-incident').click()
  await page.getByTestId('thread-write-thread-archived-incident').click()
  await expect(page.getByTestId('thread-status-thread-archived-incident')).toContainText('Open')
  await expect(page.getByTestId('thread-write-receipt')).toContainText('Thread write accepted')

  await page.getByTestId('create-forum-post-without-tag').click()
  await expect(page.getByTestId('forum-post-error')).toContainText('Select at least one tag')

  await page.getByTestId('create-forum-post-release').click()
  await expect(page.getByTestId('forum-post-error')).toContainText('Ready')
  await expect(page.getByTestId('thread-forum-release-plan')).toContainText('Release plan')
})

test('runs onboarding, AutoMod, and audit log moderation flow', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('moderation-panel')).toContainText('Moderation')
  await expect(page.getByTestId('onboarding-question')).toContainText('Choose your squad')
  await expect(page.getByTestId('automod-rule-keyword-leak')).toContainText('leak')
  await expect(page.getByTestId('audit-log')).toContainText('AUDIT_RULE_CREATED')

  await page.getByTestId('submit-onboarding-answer-engineering').click()
  await expect(page.getByTestId('onboarding-assigned-role')).toContainText('Engineering')
  await expect(page.getByTestId('audit-log')).toContainText('ONBOARDING_ROLE_ASSIGNED')

  await page.getByTestId('simulate-automod-block').click()
  await expect(page.getByTestId('automod-decision')).toContainText('Blocked before persist')
  await expect(page.getByTestId('audit-log')).toContainText('AUTOMOD_MESSAGE_BLOCKED')
  await expect(page.getByTestId('chat-viewport')).not.toContainText('leak the release token')
})

test('joins voice, toggles controls, screen shares, and leaves', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('voice-panel')).toContainText('Voice')
  await expect(page.getByTestId('voice-token-provider')).toContainText('LIVEKIT_SKELETON')
  await expect(page.getByTestId('voice-participants')).toContainText('No participants')

  await page.getByTestId('voice-join-channel-war-room').click()
  await expect(page.getByTestId('user-panel')).toContainText('Voice connected: war-room')
  await expect(page.getByTestId('voice-participants')).toContainText('vibe-coder')
  await expect(page.getByTestId('voice-events')).toContainText('VOICE_JOIN')

  await page.getByTestId('voice-toggle-mute').click()
  await page.getByTestId('voice-toggle-deaf').click()
  await page.getByTestId('voice-toggle-speaking').click()
  await page.getByTestId('voice-toggle-screen-share').click()
  await expect(page.getByTestId('voice-local-state')).toContainText('Muted')
  await expect(page.getByTestId('voice-local-state')).toContainText('Deafened')
  await expect(page.getByTestId('voice-local-state')).toContainText('Speaking')
  await expect(page.getByTestId('voice-local-state')).toContainText('Screen sharing')

  await page.getByTestId('voice-leave').click()
  await expect(page.getByTestId('user-panel')).toContainText('voice disconnected')
  await expect(page.getByTestId('voice-participants')).toContainText('No participants')
  await expect(page.getByTestId('voice-events')).toContainText('VOICE_LEAVE')
})

test('runs stage, soundboard, and premium skeleton operations', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByTestId('experience-panel')).toContainText('Experience')
  await expect(page.getByTestId('stage-topic')).toContainText('No active stage')
  await expect(page.getByTestId('premium-gate')).toContainText('Locked')

  await page.getByTestId('stage-start').click()
  await page.getByTestId('stage-request-speak').click()
  await expect(page.getByTestId('stage-topic')).toContainText('T14 roadmap live review')
  await expect(page.getByTestId('stage-pending')).toContainText('vibe-coder')
  await expect(page.getByTestId('stage-speakers')).toContainText('No speakers')

  await page.getByTestId('stage-approve-vibe-coder').click()
  await expect(page.getByTestId('stage-speakers')).toContainText('vibe-coder')
  await expect(page.getByTestId('stage-pending')).toContainText('No pending requests')

  await page.getByTestId('stage-move-audience-vibe-coder').click()
  await expect(page.getByTestId('stage-speakers')).toContainText('No speakers')
  await expect(page.getByTestId('stage-audience')).toContainText('vibe-coder')

  await page.getByTestId('soundboard-create-applause').click()
  await page.getByTestId('soundboard-play-applause').click()
  await expect(page.getByTestId('soundboard-sounds')).toContainText('Applause')
  await expect(page.getByTestId('soundboard-last-event')).toContainText('played Applause in war-room')

  await page.getByTestId('premium-check-hd-stream').click()
  await expect(page.getByTestId('premium-gate')).toContainText('Locked')

  await page.getByTestId('premium-grant-hd-stream').click()
  await page.getByTestId('premium-check-hd-stream').click()
  await expect(page.getByTestId('premium-gate')).toContainText('Unlocked')
  await expect(page.getByTestId('premium-catalog')).toContainText('HD Stream Pack')
  await expect(page.getByTestId('premium-quests')).toContainText('Stream for 10 minutes')
})

test('uses Nuxt routing for unknown routes instead of rendering the app shell', async ({ page }) => {
  await page.goto('/definitely-not-a-real-route')

  await expect(page.getByTestId('server-rail')).toHaveCount(0)
  await expect(page.locator('body')).toContainText(/404|Page not found/i)
})
