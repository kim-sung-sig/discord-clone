import { navigateTo } from '#app'
import { useAuthStore } from '../stores/auth'

const appShellPaths = new Set(['/', '/app'])

export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/login') {
    // Already signed in (in-memory access token): skip the form and open the workspace.
    // Without a token the form stays; LoginForm restores a refresh-cookie session itself.
    if (!import.meta.server && useAuthStore().accessToken) {
      return navigateTo('/', { replace: true })
    }
    return
  }

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
