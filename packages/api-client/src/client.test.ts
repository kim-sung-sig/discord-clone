import { describe, expect, it, vi } from 'vitest'
import { ApiClientError, createApiClient } from './index'

describe('api client contract wrapper', () => {
  it('sends authorization and request id headers', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 }))
    const client = createApiClient({ baseUrl: 'https://api.example.test', fetcher })

    await client.request('POST', '/api/auth/login', {
      body: { email: 'user@example.test', password: 'pass' },
      bearerToken: 'access-token',
      requestId: 'req-t42'
    })

    expect(fetcher).toHaveBeenCalledWith('https://api.example.test/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        Authorization: 'Bearer access-token',
        'Content-Type': 'application/json',
        'X-Request-Id': 'req-t42'
      },
      body: JSON.stringify({ email: 'user@example.test', password: 'pass' })
    })
  })

  it('normalizes standard api error responses', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({
      requestId: 'req-failed',
      code: 'MESSAGE_FORBIDDEN',
      message: 'You cannot send messages in this channel.',
      status: 403
    }), { status: 403 }))
    const client = createApiClient({ fetcher })

    await expect(client.request('GET', '/api/channels/{channelId}/messages', { requestId: 'req-failed' }))
      .rejects
      .toMatchObject({
        name: 'ApiClientError',
        status: 403,
        requestId: 'req-failed',
        code: 'MESSAGE_FORBIDDEN',
        message: 'You cannot send messages in this channel.'
      } satisfies Partial<ApiClientError>)
  })
})
