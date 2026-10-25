import { expect, test } from '@playwright/test'

test.use({
  viewport: { width: 390, height: 844 },
  isMobile: true,
  hasTouch: true
})

test('mobile PWA shell exposes install metadata and single-pane navigation', async ({ page }) => {
  await page.goto('/')

  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', '/manifest.webmanifest')
  await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#111827')

  const manifestResponse = await page.request.get('/manifest.webmanifest')
  expect(manifestResponse.ok()).toBe(true)
  await expect(manifestResponse.json()).resolves.toEqual(
    expect.objectContaining({
      name: 'Discord Clone',
      short_name: 'DClone',
      display: 'standalone',
      start_url: '/'
    })
  )

  const serviceWorkerResponse = await page.request.get('/sw.js')
  expect(serviceWorkerResponse.ok()).toBe(true)
  expect(await serviceWorkerResponse.text()).toContain('offline.html')

  const offlineShellResponse = await page.request.get('/offline.html')
  expect(offlineShellResponse.ok()).toBe(true)
  expect(await offlineShellResponse.text()).toContain('Offline shell ready')

  const shellMetadataResponse = await page.request.get('/pwa-shell')
  expect(shellMetadataResponse.ok()).toBe(true)
  await expect(shellMetadataResponse.json()).resolves.toEqual(
    expect.objectContaining({
      manifest: '/manifest.webmanifest',
      serviceWorker: '/sw.js',
      offlineShell: '/offline.html'
    })
  )

  await expect(page.getByTestId('mobile-shell-bar')).toContainText('# general')
  await expect(page.getByTestId('mobile-nav-channels')).toBeVisible()
  await expect(page.getByTestId('mobile-nav-chat')).toBeVisible()
  await expect(page.getByTestId('mobile-nav-members')).toBeVisible()
  await expect(page.getByTestId('mobile-nav-voice')).toBeVisible()

  await expect(page.getByTestId('chat-viewport')).toBeVisible()
  await expect(page.getByTestId('channel-sidebar')).not.toBeVisible()
  await expect(page.getByTestId('member-sidebar')).not.toBeVisible()
  await expect(page.getByTestId('voice-panel')).not.toBeVisible()

  await page.getByTestId('mobile-nav-channels').click()
  await expect(page.getByTestId('channel-sidebar')).toBeVisible()
  await expect(page.getByTestId('chat-viewport')).not.toBeVisible()
  await expect(page.getByTestId('channel-general')).toContainText('general')

  await page.getByTestId('mobile-nav-members').click()
  await expect(page.getByTestId('member-sidebar')).toBeVisible()
  await expect(page.getByTestId('chat-viewport')).not.toBeVisible()
  await expect(page.getByTestId('member-sidebar')).toContainText('Members')

  await page.getByTestId('mobile-nav-voice').click()
  await expect(page.getByTestId('voice-panel')).toBeVisible()
  await expect(page.getByTestId('voice-panel')).toContainText('Voice')

  await page.getByTestId('mobile-nav-chat').click()
  await expect(page.getByTestId('chat-viewport')).toBeVisible()
  await expect(page.getByTestId('message-input')).toBeVisible()
})

test('mobile shell uses one column, wraps message text normally, and keeps Send visible', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByTestId('chat-viewport')).toBeVisible()
  await expect(page.getByTestId('message-send')).toBeVisible()

  const metrics = await page.evaluate(() => {
    const rect = (testId: string) => document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)?.getBoundingClientRect()
    const send = rect('message-send')
    const viewport = rect('chat-viewport')
    const card = rect('message-card')
    return {
      viewportWidth: window.innerWidth,
      documentWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
      chatLeft: Math.round(viewport?.left ?? -1),
      chatWidth: Math.round(viewport?.width ?? 0),
      cardWidth: Math.round(card?.width ?? 0),
      sendLeft: Math.round(send?.left ?? -1),
      sendRight: Math.round(send?.right ?? Number.POSITIVE_INFINITY)
    }
  })

  expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  expect(metrics.chatLeft).toBeLessThanOrEqual(1)
  expect(metrics.chatWidth).toBeGreaterThanOrEqual(metrics.viewportWidth - 2)
  expect(metrics.cardWidth).toBeGreaterThan(200)
  expect(metrics.sendLeft).toBeGreaterThanOrEqual(0)
  expect(metrics.sendRight).toBeLessThanOrEqual(metrics.viewportWidth)
})
