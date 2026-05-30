import { expect, test } from '@playwright/test'

const authBody = {
  accessToken: 'browser-access-token',
  user: {
    id: 'user-browser',
    username: 'browser-user',
    displayName: 'Browser User'
  }
}

test('opens /app and hydrates the workbench from the authenticated user guild bootstrap', async ({ page }) => {
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(authBody)
    })
  })
  await page.route('**/api/users/@me/guilds', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        guilds: [
          {
            id: 'guild-browser-home',
            name: 'Browser Home',
            ownerId: 'user-browser',
            channels: [
              { id: 'channel-browser-ops', name: 'ops', type: 'GUILD_TEXT', parentId: null },
              { id: 'channel-browser-voice', name: 'standup', type: 'GUILD_VOICE', parentId: null }
            ]
          }
        ]
      })
    })
  })
  await page.route('**/api/channels/channel-browser-ops/messages**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        messages: [
          {
            id: 'message-browser-backed',
            channelId: 'channel-browser-ops',
            authorId: 'user-browser',
            content: 'Browser-backed hello from ops',
            mentions: [],
            pinned: false,
            deleted: false,
            edited: false,
            createdAt: '2026-05-22T07:30:00.000Z'
          }
        ],
        nextCursor: null
      })
    })
  })

  await page.goto('/app')

  await expect(page).toHaveURL(/\/app$/)
  await expect(page.getByTestId('vscode-shell')).toBeVisible()
  await expect(page.getByTestId('workspace-explorer')).toContainText('Browser Home')
  await expect(page.getByTestId('workspace-explorer')).toContainText('ops')
  await expect(page.getByTestId('editor-titlebar')).toContainText('# ops')
  await expect(page.getByTestId('chat-viewport')).toContainText('Browser-backed hello from ops')
  await expect(page.getByTestId('status-bar')).toContainText('READY')
})
