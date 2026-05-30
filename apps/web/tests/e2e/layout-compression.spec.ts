import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

const screenshotDir = resolve(process.cwd(), '..', '..', 'output', 'playwright', 't166-layout-compression')

test('desktop shell keeps operational panels inside the viewport without clipped controls', async ({ page }) => {
  mkdirSync(screenshotDir, { recursive: true })
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto('/')
  await expect(page.getByTestId('workspace')).toBeVisible()
  await expect(page.getByTestId('message-input')).toBeVisible()
  await page.getByTestId('bottom-panel-toggle').click()
  await expect(page.getByTestId('bottom-panel')).toBeVisible()

  const metrics = await page.evaluate(() => {
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight
    const workspace = document.querySelector<HTMLElement>('[data-testid="workspace"]')
    const selectors = [
      '[data-testid="gateway-status-panel"]',
      '[data-testid="role-permission-panel"]',
      '[data-testid="moderation-panel"]',
      '[data-testid="voice-panel"]',
      '[data-testid="experience-panel"]',
      '[data-testid="invite-modal"]',
      '[data-testid="gateway-status-panel"] .gateway-status-grid > div',
      '[data-testid="gateway-status-panel"] [data-testid="gateway-event-log"] article',
      '[data-testid="role-permission-panel"] [data-testid="permission-diff"]',
      '[data-testid="role-permission-panel"] [data-testid="preview-as-role"]',
      '[data-testid="role-permission-panel"] [data-testid="privileged-audit"]',
      '[data-testid="role-permission-panel"] button',
      '[data-testid="moderation-panel"] button',
      '[data-testid="moderation-panel"] [data-testid="audit-log"] article',
      '[data-testid="voice-panel"] button',
      '[data-testid="experience-panel"] button'
    ]
    const clipped = selectors.flatMap((selector) =>
      Array.from(document.querySelectorAll<HTMLElement>(selector)).flatMap((element, index) => {
        if (element.closest('[data-testid="legacy-shell-contracts"]')) {
          return []
        }
        const style = getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        if (style.display === 'none' || style.visibility === 'hidden' || rect.width === 0 || rect.height === 0) {
          return []
        }
        const messages: string[] = []
        if (rect.left < -1 || rect.right > viewportWidth + 1) {
          messages.push(`${selector}[${index}] outside viewport ${Math.round(rect.left)}-${Math.round(rect.right)}`)
        }
        if (element.scrollWidth > element.clientWidth + 1) {
          messages.push(`${selector}[${index}] horizontal overflow ${element.scrollWidth}/${element.clientWidth}`)
        }
        return messages
      })
    )

    return {
      viewportWidth,
      viewportHeight,
      scrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
      scrollHeight: Math.max(document.documentElement.scrollHeight, document.body.scrollHeight),
      workspaceRight: Math.round(workspace?.getBoundingClientRect().right ?? 0),
      clipped
    }
  })

  await page.screenshot({ path: resolve(screenshotDir, 'desktop-layout.png'), fullPage: true })

  expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  expect(metrics.workspaceRight).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  expect(metrics.clipped).toEqual([])
})
