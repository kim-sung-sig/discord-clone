import { defineStore } from 'pinia'
import { useRuntimeConfig } from '#app'
import { DiscordRestError, createDiscordRestClient, discordApiPaths } from '../services/discord-api'

type LoginCredentials = {
  email: string
  password: string
}

type AuthUser = {
  id: string
  username: string
  displayName: string
}

type AuthResponse = {
  accessToken: string
  user: AuthUser
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    user: null as AuthUser | null,
    error: null as string | null,
    isLoading: false
  }),
  actions: {
    async login(credentials: LoginCredentials) {
      this.error = null
      this.isLoading = true

      try {
        const config = useRuntimeConfig()
        const client = createDiscordRestClient({
          baseUrl: config.public.apiBaseUrl,
          fetcher: globalThis.fetch
        })
        const response = await client.post<AuthResponse>(discordApiPaths.auth.login(), {
          email: credentials.email,
          password: credentials.password
        })
        this.accessToken = response.accessToken
        this.user = response.user
      } catch (error) {
        this.accessToken = null
        this.user = null
        this.error = loginErrorMessage(error)
      } finally {
        this.isLoading = false
      }
    }
  }
})

function loginErrorMessage(error: unknown): string {
  if (error instanceof DiscordRestError && (error.status === 401 || error.status === 403)) {
    return 'Unable to sign in with those credentials.'
  }
  return 'Unable to reach the Discord API. Try again.'
}
