import { mkdirSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

const visualSmokeDir = resolve(process.cwd(), 'test-results', 'visual-smoke')

test('captures desktop and mobile Discord shell smoke screenshots', async ({ page }) => {
  mkdirSync(visualSmokeDir, { recursive: true })

  await page.goto('/')
  await page.getByTestId('message-input').fill('T06 visual smoke message')
  await page.getByTestId('message-send').click()
  await expect(page.getByTestId('chat-viewport')).toContainText('T06 visual smoke message')
  await expect(page.getByTestId('channel-general')).toBeVisible()
  await page.screenshot({ path: resolve(visualSmokeDir, 'desktop-shell.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByTestId('channel-general')).toBeVisible()
  await expect(page.getByTestId('message-input')).toBeVisible()
  await page.screenshot({ path: resolve(visualSmokeDir, 'mobile-shell.png'), fullPage: true })

  expect(statSync(resolve(visualSmokeDir, 'desktop-shell.png')).size).toBeGreaterThan(5000)
  expect(statSync(resolve(visualSmokeDir, 'mobile-shell.png')).size).toBeGreaterThan(5000)
})
