import { expect, test } from '@playwright/test'

const backendBaseUrl = process.env.REAL_BACKEND_BASE_URL ?? 'http://127.0.0.1:8080'
const runRealBackend = process.env.REAL_BACKEND_E2E === '1'

test.skip(!runRealBackend, 'Set REAL_BACKEND_E2E=1 to run real-backend frontend smoke')

test('signs in through the real backend and covers guild, channel, and message in the workspace', async ({ page, request }) => {
  const stamp = Date.now()
  const email = `web-real-${stamp}@example.com`
  const username = `webreal${String(stamp).slice(-8)}`
  const password = 'correct horse battery staple'

  const signup = await request.post(`${backendBaseUrl}/api/auth/signup`, {
    data: {
      email,
      username,
      displayName: 'Web Real',
      password
    }
  })
  expect(signup.ok()).toBeTruthy()

  // The workspace has no developer panel anymore, so seed the guild, channels, and first message through the API.
  const { accessToken } = await signup.json()
  const authorization = { Authorization: `Bearer ${accessToken}` }
  const guildName = `Web Real Guild ${stamp}`
  const guildResponse = await request.post(`${backendBaseUrl}/api/guilds`, {
    headers: authorization, data: { name: guildName }
  })
  expect(guildResponse.ok()).toBeTruthy()
  const guild = await guildResponse.json()
  const textResponse = await request.post(`${backendBaseUrl}/api/guilds/${guild.id}/channels`, {
    headers: authorization, data: { name: 'real-text', type: 'GUILD_TEXT', parentId: null }
  })
  expect(textResponse.ok()).toBeTruthy()
  const textChannel = await textResponse.json()
  const voiceResponse = await request.post(`${backendBaseUrl}/api/guilds/${guild.id}/channels`, {
    headers: authorization, data: { name: 'real-voice', type: 'GUILD_VOICE', parentId: null }
  })
  expect(voiceResponse.ok()).toBeTruthy()
  const voiceChannel = await voiceResponse.json()
  const seeded = await request.post(`${backendBaseUrl}/api/channels/${textChannel.id}/messages`, {
    headers: authorization, data: { content: 'real backend hello', idempotencyKey: `web-real-seed-${stamp}` }
  })
  expect(seeded.ok()).toBeTruthy()

  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('login-submit')).toBeEnabled()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  // The login form may or may not navigate by itself, so wait for the login response and open the
  // workspace explicitly; the session is restored from the refresh cookie.
  const loginResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname.endsWith('/api/auth/login') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Sign in' }).click()
  expect((await loginResponse).ok()).toBeTruthy()

  await page.goto('/')
  await expect(page.getByTestId('workspace')).toBeVisible()

  await expect(page.getByTestId('real-api-panel')).toHaveCount(0)
  await expect(page.getByTestId('real-backend-smoke')).toHaveCount(0)
  await expect(page.getByTestId('user-panel')).toContainText('Web Real')

  await expect(page.getByTestId('workspace-explorer')).toContainText(guildName)
  await expect(page.getByTestId('guild-name')).toContainText(guildName)
  await expect(page.getByTestId(textChannel.id)).toContainText('real-text')
  await expect(page.getByTestId(voiceChannel.id)).toContainText('real-voice')
  await expect(page.getByTestId('chat-viewport')).toContainText('real backend hello')

  await page.getByTestId('message-input').fill('sent from the workspace composer')
  await page.getByTestId('message-send').click()
  await expect(page.getByTestId('chat-viewport')).toContainText('sent from the workspace composer')
  await expect(page.getByTestId('message-input')).toHaveValue('')

  const persistedClientState = await page.evaluate(() => ({
    localStorage: { ...window.localStorage },
    sessionStorage: { ...window.sessionStorage },
    documentCookie: document.cookie
  }))
  const cookies = await page.context().cookies()

  expect(JSON.stringify({ ...persistedClientState, cookies })).not.toContain('accessToken')
})
