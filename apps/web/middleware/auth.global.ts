import { navigateTo } from '#app'
import { useAuthStore } from '../stores/auth'

const appShellPaths = new Set(['/', '/app'])

export default defineNuxtRouteMiddleware(async (to) => {
  if (!appShellPaths.has(to.path)) {
    return
  }

  if (import.meta.server) {
    return
  }

  const auth = useAuthStore()
  if (auth.accessToken) {
    return
  }

  const restored = await auth.restoreSession()
  if (!restored) {
    return navigateTo('/login', { replace: true })
  }
})
