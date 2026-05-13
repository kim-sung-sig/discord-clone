import { expect, test } from '@playwright/test'

test('logs in locally from the login page', async ({ page }) => {
  await page.goto('/login')

  await page.getByLabel('Email').fill('user@example.com')
  await page.getByLabel('Password').fill('correct-password')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByTestId('login-success')).toContainText('Signed in locally')
  await expect(page.getByTestId('login-token-policy')).toContainText(
    'Access token is stored in memory for this frontend slice.'
  )

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

  expect(JSON.stringify({ ...persistedClientState, cookies })).not.toContain(
    'local-placeholder-access-token'
  )
})
