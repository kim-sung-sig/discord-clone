import { defineNitroPlugin } from 'nitropack/runtime'
import { defaultCspReportRateLimiter } from '../utils/csp-report-rate-limiter'

export default defineNitroPlugin((nitroApp) => {
  nitroApp.hooks.hook('close', async () => {
    await defaultCspReportRateLimiter.close?.()
  })
})
