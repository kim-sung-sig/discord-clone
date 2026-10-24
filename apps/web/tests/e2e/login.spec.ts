import { expect, test } from '@playwright/test'

test('shows the login form in the browser language without developer copy', async ({ page }) => {
  await page.goto('/login')
  await page.waitForLoadState('networkidle')

  await expect(page.locator('html')).toHaveAttribute('lang', /^(en-US|ko-KR)$/)
  await expect(page.getByTestId('login-form')).toBeVisible()
  await expect(page.locator('body')).not.toContainText(/Spring Boot|httpOnly|access token/i)
  await expect(page.getByTestId('login-token-policy')).toHaveCount(0)
})

test('logs in through the API from the login page with memory-only access token and refresh cookie', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'dc_refresh=e2e-refresh-token; Path=/api/auth; HttpOnly; SameSite=Lax'
      },
      body: JSON.stringify({
        accessToken: 'backend-e2e-access-token',
        user: {
          id: 'user-e2e',
          username: 'user',
          displayName: 'User'
        }
      })
    })
  })
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await expect(page.getByTestId('login-submit')).toBeEnabled()

  await page.getByLabel('Email').fill('user@example.com')
  await page.getByLabel('Password').fill('correct horse battery staple')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByTestId('workspace')).toBeVisible()
  await expect(page.getByTestId('login-form')).toHaveCount(0)

  const persistedClientState = await page.evaluate(() => {
    const entriesFor = (storage: Storage) =>
      Object.fromEntries(Array.from({ length: storage.length }, (_, index) => {
        const key = storage.key(index) ?? ''
        return [key, storage.getItem(key)]
      }))

    return {
      localStorage: entriesFor(window.localStorage),
      sessionStorage: entriesFor(window.sessionStorage),
      documentCookie: document.cookie
    }
  })
  const cookies = await page.context().cookies()

  expect(JSON.stringify(persistedClientState.localStorage)).not.toContain('backend-e2e-access-token')
  expect(JSON.stringify(cookies)).not.toContain('backend-e2e-access-token')
  expect(JSON.stringify(persistedClientState.sessionStorage)).not.toContain('backend-e2e-access-token')
  expect(JSON.stringify(cookies)).toContain('dc_refresh')
})
