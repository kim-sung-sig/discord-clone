import { expect, test } from '@playwright/test'

test('logs in through the API from the login page with session-only token restore', async ({ page }) => {
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
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

  await expect(page.getByTestId('login-success')).toContainText('Signed in with backend session')
  await expect(page.getByTestId('login-token-policy')).toContainText(
    'Access token is stored in session storage for this browser tab.'
  )
  await expect(page.getByTestId('open-workspace')).toBeVisible()

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
  expect(JSON.stringify(persistedClientState.sessionStorage)).toContain('backend-e2e-access-token')
})
