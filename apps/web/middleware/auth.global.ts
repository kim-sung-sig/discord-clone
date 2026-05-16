import { useAuthStore } from '../stores/auth'

export default defineNuxtRouteMiddleware(() => {
  const auth = useAuthStore()
  void auth.restoreSession()
})
