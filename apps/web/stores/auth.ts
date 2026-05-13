import { defineStore } from 'pinia'

type LoginCredentials = {
  email: string
  password: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    error: null as string | null
  }),
  actions: {
    async login(credentials: LoginCredentials) {
      this.error = null

      if (credentials.email.trim() && credentials.password === 'correct-password') {
        this.accessToken = 'local-placeholder-access-token'
        return
      }

      this.accessToken = null
      this.error = 'Unable to sign in with those credentials.'
    }
  }
})
