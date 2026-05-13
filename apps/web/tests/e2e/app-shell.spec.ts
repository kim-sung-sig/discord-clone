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
  await expect(page.getByTestId('member-sidebar')).toContainText('Members')
  await expect(page.getByTestId('member-sidebar')).toContainText('online')
  await expect(page.getByTestId('user-panel')).toContainText('vibe-coder')
})

test('selects a text channel and updates active channel content', async ({ page }) => {
  await page.goto('/')

  await page.getByTestId('channel-architecture').click()

  await expect(page.getByTestId('active-channel')).toContainText('# architecture')
  await expect(page.locator('[data-channel-id="channel-architecture"]')).toHaveAttribute('aria-current', 'page')
  await expect(page.getByTestId('chat-viewport')).toContainText('Architecture notes belong in this channel')
})

test('uses Nuxt routing for unknown routes instead of rendering the app shell', async ({ page }) => {
  await page.goto('/definitely-not-a-real-route')

  await expect(page.getByTestId('server-rail')).toHaveCount(0)
  await expect(page.locator('body')).toContainText(/404|Page not found/i)
})
