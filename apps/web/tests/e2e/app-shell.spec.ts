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

test('uses Nuxt routing for unknown routes instead of rendering the app shell', async ({ page }) => {
  await page.goto('/definitely-not-a-real-route')

  await expect(page.getByTestId('server-rail')).toHaveCount(0)
  await expect(page.locator('body')).toContainText(/404|Page not found/i)
})
