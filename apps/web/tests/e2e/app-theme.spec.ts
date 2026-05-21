import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

const screenshotDir = resolve(process.cwd(), '..', '..', 'output', 'playwright', 'app-theme')

test('desktop shell uses the Discord and VSCode dark theme across primary surfaces', async ({ page }) => {
  mkdirSync(screenshotDir, { recursive: true })
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto('/')

  await expect(page.getByTestId('global-title-bar')).toBeVisible()
  await expect(page.getByTestId('account-banner')).toBeVisible()
  await expect(page.getByTestId('server-rail')).toBeVisible()
  await expect(page.getByTestId('workspace')).toBeVisible()

  const theme = await page.evaluate(() => {
    const colorOf = (selector: string) => {
      const element = document.querySelector<HTMLElement>(selector)
      if (!element) {
        return null
      }
      return getComputedStyle(element).backgroundColor
    }
    const rootStyle = getComputedStyle(document.documentElement)
    const bodyStyle = getComputedStyle(document.body)
    const serverRail = document.querySelector<HTMLElement>('[data-testid="server-rail"]')
    const topBar = document.querySelector<HTMLElement>('[data-testid="global-title-bar"]')
    const workspace = document.querySelector<HTMLElement>('[data-testid="workspace"]')

    return {
      bodyBackground: bodyStyle.backgroundColor,
      bodyBackgroundImage: bodyStyle.backgroundImage,
      discordBg: rootStyle.getPropertyValue('--discord-bg').trim(),
      discordPanel: rootStyle.getPropertyValue('--discord-panel').trim(),
      vscodeFocus: rootStyle.getPropertyValue('--vscode-focus').trim(),
      topBarHeight: Math.round(topBar?.getBoundingClientRect().height ?? 0),
      serverRailWidth: Math.round(serverRail?.getBoundingClientRect().width ?? 0),
      workspaceTop: Math.round(workspace?.getBoundingClientRect().top ?? 0),
      serverRailBackground: colorOf('[data-testid="server-rail"]'),
      channelSidebarBackground: colorOf('[data-testid="channel-sidebar"]'),
      dmSidebarBackground: colorOf('[data-testid="dm-sidebar"]'),
      chatBackground: colorOf('[data-testid="chat-viewport"]'),
      memberSidebarBackground: colorOf('[data-testid="member-sidebar"]')
    }
  })

  await page.screenshot({ path: resolve(screenshotDir, 'discord-vscode-theme.png'), fullPage: true })

  expect(theme.discordBg).toBe('#111214')
  expect(theme.discordPanel).toBe('#1e1f22')
  expect(theme.vscodeFocus).toBe('#0078d4')
  expect(theme.bodyBackground).toBe('rgb(17, 18, 20)')
  expect(theme.bodyBackgroundImage).toBe('none')
  expect(theme.topBarHeight).toBe(30)
  expect(theme.serverRailWidth).toBe(72)
  expect(theme.workspaceTop).toBe(68)
  expect(theme.serverRailBackground).toBe('rgb(30, 31, 34)')
  expect(theme.channelSidebarBackground).toBe('rgb(43, 45, 49)')
  expect(theme.dmSidebarBackground).toBe('rgb(43, 45, 49)')
  expect(theme.chatBackground).toBe('rgb(49, 51, 56)')
  expect(theme.memberSidebarBackground).toBe('rgb(43, 45, 49)')
})
