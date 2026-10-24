import { defineStore } from 'pinia'
import { useRuntimeConfig } from '#app'
import { DiscordRestError } from '@discord-clone/api-client'
import type { AuthResponse as ApiAuthResponse, AuthSessionResponse, AuthUserResponse } from '@discord-clone/api-client'
import { authLogin, authLogout, authRefresh, authRevokeSession, authSessions } from '@discord-clone/api-client/endpoints/auth'
import { usePreferencesStore } from './preferences'
import { useShellStore } from './shell'

type LoginCredentials = {
  email: string
  password: string
}

type AuthUser = Pick<AuthUserResponse, 'id' | 'username' | 'displayName'>

type AuthResponse = Pick<ApiAuthResponse, 'accessToken'> & { user: AuthUser }

export type AuthSession = AuthSessionResponse

const isClient = () => typeof window !== 'undefined'

const apiOptions = (bearerToken?: string) => ({
  baseUrl: useRuntimeConfig().public.apiBaseUrl,
  ...(bearerToken ? { bearerToken } : {})
})

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    user: null as AuthUser | null,
    sessions: [] as AuthSession[],
    error: null as string | null,
    sessionError: null as string | null,
    isLoading: false,
    isRestoring: false,
    sessionGeneration: 0,
    isApplyingSession: false,
    restorePromise: null as Promise<boolean> | null
  }),
  actions: {
    acceptSession(response: AuthResponse): void {
      if (!this.user || this.user.id !== response.user.id) {
        useShellStore().resetAccountState()
      }
      this.isApplyingSession = true
      this.$patch({ accessToken: response.accessToken, user: response.user, sessions: [], error: null })
      this.isApplyingSession = false
    },
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
      const generation = this.sessionGeneration
      this.restorePromise = authRefresh(apiOptions())
        .then((response) => {
          if (generation !== this.sessionGeneration) return false
          this.acceptSession(response)
          return true
        })
        .catch(() => {
          if (generation !== this.sessionGeneration) return false
          if (this.user) useShellStore().resetAccountState()
          this.accessToken = null
          this.user = null
          this.sessions = []
          return false
        })
        .finally(() => {
          if (generation !== this.sessionGeneration) return
          this.isRestoring = false
          this.restorePromise = null
        })

      return this.restorePromise
    },
    async login(credentials: LoginCredentials) {
      const generation = ++this.sessionGeneration
      this.restorePromise = null
      this.isRestoring = false
      this.error = null
      this.isLoading = true

      try {
        const response = await authLogin({
          email: credentials.email,
          password: credentials.password
        }, apiOptions())
        if (generation !== this.sessionGeneration) return
        this.acceptSession(response)
      } catch (error) {
        if (generation !== this.sessionGeneration) return
        useShellStore().resetAccountState()
        this.accessToken = null
        this.user = null
        this.sessions = []
        this.error = loginErrorMessage(error)
      } finally {
        if (generation === this.sessionGeneration) this.isLoading = false
      }
    },
    async loadSessions() {
      if (!this.accessToken) {
        this.sessions = []
        return []
      }
      this.sessionError = null
      const generation = this.sessionGeneration
      const token = this.accessToken
      try {
        const { sessions } = await authSessions(apiOptions(token))
        if (generation !== this.sessionGeneration || token !== this.accessToken) return []
        this.sessions = sessions
        return this.sessions
      } catch (error) {
        if (generation !== this.sessionGeneration || token !== this.accessToken) return []
        this.sessionError = sessionErrorMessage(error)
        return this.sessions
      }
    },
    async revokeSession(sessionId: string) {
      if (!this.accessToken) {
        return
      }
      this.sessionError = null
      const generation = this.sessionGeneration
      const token = this.accessToken
      try {
        await authRevokeSession(sessionId, apiOptions(token))
        if (generation !== this.sessionGeneration || token !== this.accessToken) return
        this.sessions = this.sessions.map((session) =>
          session.id === sessionId ? { ...session, revoked: true } : session
        )
      } catch (error) {
        if (generation !== this.sessionGeneration || token !== this.accessToken) return
        this.sessionError = sessionErrorMessage(error)
      }
    },
    async logout() {
      const token = this.accessToken
      ++this.sessionGeneration
      useShellStore().resetAccountState()
      this.$patch({ accessToken: null, user: null, sessions: [], error: null, sessionError: null,
        restorePromise: null, isRestoring: false, isLoading: false })
      try {
        await authLogout(apiOptions(token ?? undefined))
      } catch {
        // Local state must be cleared even if the network path is unavailable.
      }
    }
  }
})

function loginErrorMessage(error: unknown): string {
  const preferences = usePreferencesStore()
  if (error instanceof DiscordRestError && error.code === 'LOGIN_LOCKED') {
    return preferences.t('login.error.locked')
  }
  if (error instanceof DiscordRestError && error.code === 'FORBIDDEN') {
    return preferences.t('login.error.forbidden')
  }
  if (error instanceof DiscordRestError && (error.status === 401 || error.status === 403)) {
    return preferences.t('login.error.invalidCredentials')
  }
  return preferences.t('login.error.unreachable')
}

function sessionErrorMessage(error: unknown): string {
  if (error instanceof DiscordRestError && error.status === 401) {
    return 'Session expired. Sign in again.'
  }
  return 'Unable to update session state. Try again.'
}
