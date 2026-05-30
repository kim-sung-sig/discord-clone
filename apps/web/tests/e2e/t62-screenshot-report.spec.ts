import { mkdirSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

const screenshotDir = resolve(process.cwd(), '..', '..', 'output', 'playwright', 't62-subscription-reconciliation')

test('captures T62 Discord clone screenshot report evidence', async ({ page }) => {
  mkdirSync(screenshotDir, { recursive: true })

  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.goto('/')
  await expect(page.getByTestId('gateway-status-panel')).toContainText('Gateway')
  await expect(page.getByTestId('gateway-resumed')).toContainText('Session resumed')
  await page.screenshot({ path: resolve(screenshotDir, '01-desktop-gateway-shell.png'), fullPage: true })

  await page.getByTestId('voice-join-channel-war-room').click()
  await page.getByTestId('voice-toggle-mute').click()
  await page.getByTestId('voice-toggle-screen-share').click()
  await expect(page.getByTestId('voice-local-state')).toContainText('Screen sharing')
  await page.screenshot({ path: resolve(screenshotDir, '02-voice-state.png'), fullPage: true })

  await page.getByTestId('stage-start').click()
  await page.getByTestId('stage-request-speak').click()
  await page.getByTestId('stage-approve-vibe-coder').click()
  await expect(page.getByTestId('stage-speakers')).toContainText('vibe-coder')
  await page.screenshot({ path: resolve(screenshotDir, '03-stage-premium.png'), fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByTestId('mobile-shell-bar')).toContainText('# general')
  await page.screenshot({ path: resolve(screenshotDir, '04-mobile-chat.png'), fullPage: true })

  for (const fileName of [
    '01-desktop-gateway-shell.png',
    '02-voice-state.png',
    '03-stage-premium.png',
    '04-mobile-chat.png'
  ]) {
    expect(statSync(resolve(screenshotDir, fileName)).size).toBeGreaterThan(5000)
  }
})
