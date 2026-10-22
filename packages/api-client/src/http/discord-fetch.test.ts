import { describe, expect, it, vi } from 'vitest'
import { DiscordRestError, discordFetch } from './discord-fetch'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('discordFetch mutator', () => {
  it('joins the base url and sends credentials, bearer and request id headers', async () => {
    const fetcher = vi.fn(async () => json({ ok: true }))

    const body = await discordFetch<{ ok: boolean }>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'user@example.test' }),
      baseUrl: 'https://api.example.test/',
      fetcher,
      bearerToken: 'access-token',
      requestId: 'req-t213'
    })

    expect(body).toEqual({ ok: true })
    expect(fetcher).toHaveBeenCalledWith('https://api.example.test/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': 'req-t213',
        Authorization: 'Bearer access-token'
      },
      body: JSON.stringify({ email: 'user@example.test' })
    })
  })

  it('generates a request id and omits authorization without a bearer token', async () => {
    const fetcher = vi.fn(async () => json([]))

    await discordFetch('/api/auth/sessions', { method: 'GET', fetcher })

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit]
    const headers = init.headers as Record<string, string>
    expect(url).toBe('/api/auth/sessions')
    expect(headers['X-Request-Id']).toMatch(/^web-[a-z0-9]+-[a-z0-9]+$/)
    expect(headers).not.toHaveProperty('Authorization')
    expect(init).not.toHaveProperty('bearerToken')
    expect(init).not.toHaveProperty('fetcher')
  })

  it('resolves 204 responses to undefined', async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 }))

    await expect(discordFetch('/api/auth/logout', { method: 'POST', fetcher })).resolves.toBeUndefined()
  })

  it('throws DiscordRestError with status and parsed body for failures', async () => {
    const fetcher = vi.fn(async () => json({ message: 'invalid credentials' }, 401))

    const failure = await discordFetch('/api/auth/login', { method: 'POST', fetcher }).catch((error) => error)

    expect(failure).toBeInstanceOf(DiscordRestError)
    expect(failure).toMatchObject({ status: 401, body: { message: 'invalid credentials' } })
    expect(failure.message).toBe('Discord REST request failed with 401')
  })

  it('keeps an undefined body when the error response is not json', async () => {
    const fetcher = vi.fn(async () => new Response('gateway timeout', { status: 504 }))

    const failure = await discordFetch('/api/auth/refresh', { method: 'POST', fetcher }).catch((error) => error)

    expect(failure).toMatchObject({ status: 504, body: undefined })
  })
})
