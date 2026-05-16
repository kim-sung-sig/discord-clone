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

export type AuthSession = {
  id: string
  deviceName: string
  createdAt: string
  expiresAt: string
  revoked: boolean
}

const isClient = () => typeof window !== 'undefined'

type SessionsResponse = {
  sessions: AuthSession[]
}

const authClient = () => {
  const config = useRuntimeConfig()
  return createDiscordRestClient({
    baseUrl: config.public.apiBaseUrl,
    fetcher: globalThis.fetch
  })
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    user: null as AuthUser | null,
    sessions: [] as AuthSession[],
    error: null as string | null,
    sessionError: null as string | null,
    isLoading: false,
    isRestoring: false,
    restorePromise: null as Promise<boolean> | null
  }),
  actions: {
    async restoreSession() {
      if (!isClient()) {
        return false
      }
      if (this.accessToken) {
        return true
      }
      if (this.restorePromise) {
        return this.restorePromise
      }

      this.isRestoring = true
      this.restorePromise = authClient()
        .post<AuthResponse>(discordApiPaths.auth.refresh())
        .then((response) => {
          this.accessToken = response.accessToken
          this.user = response.user
          this.error = null
          return true
        })
        .catch(() => {
          this.accessToken = null
          this.user = null
          this.sessions = []
          return false
        })
        .finally(() => {
          this.isRestoring = false
          this.restorePromise = null
        })

      return this.restorePromise
    },
    async login(credentials: LoginCredentials) {
      this.error = null
      this.isLoading = true

      try {
        const response = await authClient().post<AuthResponse>(discordApiPaths.auth.login(), {
          email: credentials.email,
          password: credentials.password
        })
        this.accessToken = response.accessToken
        this.user = response.user
        this.sessions = []
      } catch (error) {
        this.accessToken = null
        this.user = null
        this.sessions = []
        this.error = loginErrorMessage(error)
      } finally {
        this.isLoading = false
      }
    },
    async loadSessions() {
      if (!this.accessToken) {
        this.sessions = []
        return []
      }
      this.sessionError = null
      try {
        const response = await authClient().get<SessionsResponse>(discordApiPaths.auth.sessions(), {
          bearerToken: this.accessToken
        })
        this.sessions = response.sessions
        return this.sessions
      } catch (error) {
        this.sessionError = sessionErrorMessage(error)
        return this.sessions
      }
    },
    async revokeSession(sessionId: string) {
      if (!this.accessToken) {
        return
      }
      this.sessionError = null
      try {
        await authClient().delete<void>(discordApiPaths.auth.session(sessionId), {
          bearerToken: this.accessToken
        })
        this.sessions = this.sessions.map((session) =>
          session.id === sessionId ? { ...session, revoked: true } : session
        )
      } catch (error) {
        this.sessionError = sessionErrorMessage(error)
      }
    },
    async logout() {
      const token = this.accessToken
      try {
        await authClient().post<void>(
          discordApiPaths.auth.logout(),
          undefined,
          token ? { bearerToken: token } : undefined
        )
      } catch {
        // Local state must be cleared even if the network path is unavailable.
      }
      this.accessToken = null
      this.user = null
      this.sessions = []
      this.error = null
      this.sessionError = null
    }
  }
})

function loginErrorMessage(error: unknown): string {
  if (error instanceof DiscordRestError && (error.status === 401 || error.status === 403)) {
    return 'Unable to sign in with those credentials.'
  }
  return 'Unable to reach the Discord API. Try again.'
}

function sessionErrorMessage(error: unknown): string {
  if (error instanceof DiscordRestError && error.status === 401) {
    return 'Session expired. Sign in again.'
  }
  return 'Unable to update session state. Try again.'
}
