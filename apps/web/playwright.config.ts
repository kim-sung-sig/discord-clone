import { defineConfig, devices } from '@playwright/test'
import { fileURLToPath } from 'node:url'

const devPort = process.env.NUXT_DEV_PORT ?? '3000'
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? `http://127.0.0.1:${devPort}`
const workspaceRoot = fileURLToPath(new URL('../..', import.meta.url))

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  webServer: {
    command: `npx nuxt dev --host 127.0.0.1 --port ${devPort}`,
    env: {
      ...process.env,
      DISCORD_WORKSPACE_ROOT: process.env.DISCORD_WORKSPACE_ROOT ?? workspaceRoot
    },
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
