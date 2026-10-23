import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'

type Call = { url: string, init: RequestInit }

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

const stubFetch = (respond: (url: string) => Response) => {
  const calls: Call[] = []
  vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} })
    return respond(String(input))
  })
  return calls
}

const headerOf = (call: Call | undefined, name: string) => (call?.init.headers as Record<string, string> | undefined)?.[name]

describe('auth store transport (generated API client)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('restores a session with a credentialed refresh call', async () => {
    const calls = stubFetch(() => json({ accessToken: 'refreshed', user: { id: 'u1', username: 'user', displayName: 'User' } }))
    const auth = useAuthStore()

    await expect(auth.restoreSession()).resolves.toBe(true)

    expect(calls).toHaveLength(1)
    expect(calls[0]?.url).toMatch(/\/api\/auth\/refresh$/)
    expect(calls[0]?.init.method).toBe('POST')
    expect(calls[0]?.init.credentials).toBe('include')
    expect(headerOf(calls[0], 'X-Request-Id')).toMatch(/^web-/)
    expect(auth.accessToken).toBe('refreshed')
    expect(auth.user).toEqual({ id: 'u1', username: 'user', displayName: 'User' })
  })

  it('lists and revokes sessions with the bearer token', async () => {
    const session = { id: 's1', deviceName: 'Chrome', createdAt: '2026-10-23T00:00:00Z', expiresAt: '2026-11-23T00:00:00Z', revoked: false }
    const calls = stubFetch((url) => (url.endsWith('/api/auth/sessions') ? json({ sessions: [session] }) : new Response(null, { status: 204 })))
    const auth = useAuthStore()
    auth.$patch({ accessToken: 'access', user: { id: 'u1', username: 'user', displayName: 'User' } })

    await expect(auth.loadSessions()).resolves.toEqual([session])
    await auth.revokeSession('s1')

    expect(calls.map((call) => [call.init.method, call.url.replace(/^.*(\/api\/)/, '$1')])).toEqual([
      ['GET', '/api/auth/sessions'],
      ['DELETE', '/api/auth/sessions/s1']
    ])
    expect(calls.every((call) => headerOf(call, 'Authorization') === 'Bearer access')).toBe(true)
    expect(auth.sessions).toEqual([{ ...session, revoked: true }])
    expect(auth.sessionError).toBeNull()
  })

  it('encodes path parameters so an id cannot redirect the bearer request', async () => {
    const calls = stubFetch(() => new Response(null, { status: 204 }))
    const auth = useAuthStore()
    auth.$patch({ accessToken: 'access', user: { id: 'u1', username: 'user', displayName: 'User' } })

    await auth.revokeSession('../../users/@me?x=1#y')

    expect(calls[0]?.url).toMatch(/\/api\/auth\/sessions\/\.\.%2F\.\.%2Fusers%2F%40me%3Fx%3D1%23y$/)
  })

  it('logs out with the previous bearer token and clears local state even when the call fails', async () => {
    const calls = stubFetch(() => json({ message: 'unavailable' }, 503))
    const auth = useAuthStore()
    auth.$patch({ accessToken: 'access', user: { id: 'u1', username: 'user', displayName: 'User' } })

    await auth.logout()

    expect(calls[0]?.url).toMatch(/\/api\/auth\/logout$/)
    expect(calls[0]?.init.method).toBe('POST')
    expect(headerOf(calls[0], 'Authorization')).toBe('Bearer access')
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('maps an expired session to the sign-in-again message', async () => {
    stubFetch(() => json({ message: 'expired' }, 401))
    const auth = useAuthStore()
    auth.$patch({ accessToken: 'access', user: { id: 'u1', username: 'user', displayName: 'User' } })

    await auth.loadSessions()

    expect(auth.sessionError).toBe('Session expired. Sign in again.')
  })

  it('accepts the required generated login response', async () => {
    const user = { id: 'u1', username: 'user', displayName: 'User' }
    const calls = stubFetch(() => json({ accessToken: 'access', user }))
    const auth = useAuthStore()

    await auth.login({ email: 'user@example.com', password: 'secret' })

    expect(calls[0]?.url).toMatch(/\/api\/auth\/login$/)
    expect(calls[0]?.init.method).toBe('POST')
    expect(calls[0]?.init.body).toBe(JSON.stringify({ email: 'user@example.com', password: 'secret' }))
    expect(headerOf(calls[0], 'Content-Type')).toBe('application/json')
    expect(auth.accessToken).toBe('access')
    expect(auth.user).toEqual(user)
    expect(auth.error).toBeNull()
  })

  it.each([
    ['LOGIN_LOCKED', 'Account locked. Try again later.'],
    ['INVALID_CREDENTIALS', 'Unable to sign in with those credentials.'],
    ['FORBIDDEN', 'Unable to sign in. Access denied.']
  ])('distinguishes %s by code regardless of the server sentence', async (code, expected) => {
    stubFetch(() => json({ type: 'about:blank', title: 'Forbidden', status: 403,
      detail: 'Same sentence for every case', instance: '/api/auth/login', code, requestId: 'request-login' }, 403))
    const auth = useAuthStore()
    await auth.login({ email: 'user@example.com', password: 'secret' })
    expect(auth.error).toBe(expected)
    expect(auth.accessToken).toBeNull()
  })
})
