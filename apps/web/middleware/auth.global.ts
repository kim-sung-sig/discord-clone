import { useAuthStore } from '../stores/auth'

export default defineNuxtRouteMiddleware(() => {
  const auth = useAuthStore()
  auth.restoreSession()
})
