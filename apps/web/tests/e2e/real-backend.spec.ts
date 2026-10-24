import { expect, test } from '@playwright/test'

const backendBaseUrl = process.env.REAL_BACKEND_BASE_URL ?? 'http://127.0.0.1:8080'
const runRealBackend = process.env.REAL_BACKEND_E2E === '1'

test.skip(!runRealBackend, 'Set REAL_BACKEND_E2E=1 to run real-backend frontend smoke')

test('signs in through the real backend and opens a workspace without developer tooling', async ({ page, request }) => {
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

  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('login-submit')).toBeEnabled()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('login-success')).toContainText('Signed in with backend session')

  await page.getByTestId('open-workspace').click()
  await expect(page.getByTestId('workspace')).toBeVisible()

  await expect(page.getByTestId('real-api-panel')).toHaveCount(0)
  await expect(page.getByTestId('real-backend-smoke')).toHaveCount(0)
  await expect(page.getByTestId('user-panel')).toContainText('Web Real')

  const persistedClientState = await page.evaluate(() => ({
    localStorage: { ...window.localStorage },
    sessionStorage: { ...window.sessionStorage },
    documentCookie: document.cookie
  }))
  const cookies = await page.context().cookies()

  expect(JSON.stringify({ ...persistedClientState, cookies })).not.toContain('accessToken')
})
