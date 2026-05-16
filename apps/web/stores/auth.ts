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

const AUTH_SESSION_KEY = 'discord-clone.auth'

type AuthSessionSnapshot = {
  accessToken: string
  user: AuthUser
}

const isClient = () => typeof window !== 'undefined'

const readSessionSnapshot = (): AuthSessionSnapshot | null => {
  if (!isClient()) {
    return null
  }
  const raw = window.sessionStorage.getItem(AUTH_SESSION_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthSessionSnapshot>
    if (typeof parsed.accessToken === 'string' && parsed.user) {
      return parsed as AuthSessionSnapshot
    }
  } catch {
    window.sessionStorage.removeItem(AUTH_SESSION_KEY)
  }
  return null
}

const writeSessionSnapshot = (snapshot: AuthSessionSnapshot | null) => {
  if (!isClient()) {
    return
  }
  if (!snapshot) {
    window.sessionStorage.removeItem(AUTH_SESSION_KEY)
    return
  }
  window.sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(snapshot))
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    user: null as AuthUser | null,
    error: null as string | null,
    isLoading: false
  }),
  actions: {
    restoreSession() {
      const snapshot = readSessionSnapshot()
      if (!snapshot) {
        return false
      }
      this.accessToken = snapshot.accessToken
      this.user = snapshot.user
      this.error = null
      return true
    },
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
        writeSessionSnapshot({
          accessToken: response.accessToken,
          user: response.user
        })
      } catch (error) {
        this.accessToken = null
        this.user = null
        writeSessionSnapshot(null)
        this.error = loginErrorMessage(error)
      } finally {
        this.isLoading = false
      }
    },
    logout() {
      this.accessToken = null
      this.user = null
      this.error = null
      writeSessionSnapshot(null)
    }
  }
})

function loginErrorMessage(error: unknown): string {
  if (error instanceof DiscordRestError && (error.status === 401 || error.status === 403)) {
    return 'Unable to sign in with those credentials.'
  }
  return 'Unable to reach the Discord API. Try again.'
}
