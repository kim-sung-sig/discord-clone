import { mountSuspended } from '@nuxt/test-utils/runtime'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import App from '../../app.vue'
import { discordApiPaths, createDiscordRestClient } from '../../services/discord-api'
import { isGatewayDispatch, toShellGatewayEvent } from '../../services/gateway-client'
import { useShellStore } from '../../stores/shell'

describe('Discord shell API and Gateway contracts', () => {
  it('builds stable REST paths for auth, guild, channel, invite, and gateway calls', () => {
    expect(discordApiPaths.authLogin()).toBe('/api/auth/login')
    expect(discordApiPaths.guild('guild-1')).toBe('/api/guilds/guild-1')
    expect(discordApiPaths.channelMessages('channel-1', { before: 'msg-9', limit: 25 })).toBe(
      '/api/channels/channel-1/messages?before=msg-9&limit=25'
    )
    expect(discordApiPaths.invitePreview('abc123')).toBe('/api/invites/abc123')
    expect(discordApiPaths.gatewayEvents('session-1', 42)).toBe('/api/gateway/events?sessionId=session-1&afterSequence=42')
  })

  it('creates a fetch client that preserves method, path, and JSON body', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    const fetcher: typeof fetch = async (input, init) => {
      calls.push({ input, init })
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    }

    const client = createDiscordRestClient({ baseUrl: 'https://discord.local', fetcher })
    await expect(client.post('/api/channels/channel-1/messages', { body: 'hello' })).resolves.toEqual({ ok: true })

    expect(calls[0]?.input).toBe('https://discord.local/api/channels/channel-1/messages')
    expect(calls[0]?.init?.method).toBe('POST')
    expect(calls[0]?.init?.body).toBe(JSON.stringify({ body: 'hello' }))
  })

  it('sends provided request ids for REST correlation', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    const fetcher: typeof fetch = async (input, init) => {
      calls.push({ input, init })
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    }

    const client = createDiscordRestClient({ fetcher })
    await client.get('/api/premium/catalog', { requestId: 'qa-request-123' })

    expect((calls[0]?.init?.headers as Record<string, string>)['X-Request-Id']).toBe('qa-request-123')
  })

  it('generates request ids when the caller omits one', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    const fetcher: typeof fetch = async (input, init) => {
      calls.push({ input, init })
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    }

    const client = createDiscordRestClient({ fetcher })
    await client.get('/api/premium/catalog')

    const requestId = (calls[0]?.init?.headers as Record<string, string>)['X-Request-Id']
    expect(requestId).toMatch(/^web-[a-z0-9._-]{8,64}$/)
  })

  it('validates and maps gateway dispatches into shell gateway events', () => {
    const dispatch = {
      sequence: 43,
      type: 'MESSAGE_CREATE',
      payload: { channelId: 'channel-general', content: 'hello' }
    }

    expect(isGatewayDispatch(dispatch)).toBe(true)
    expect(toShellGatewayEvent(dispatch)).toEqual({
      sequence: 43,
      type: 'MESSAGE_CREATE',
      label: 'message create dispatch accepted'
    })
    expect(isGatewayDispatch({ sequence: 0, type: '', payload: {} })).toBe(false)
  })

  it('applies validated gateway dispatches to the shell store while rejecting stale sequences', async () => {
    const wrapper = await mountSuspended(App)
    const shell = useShellStore()

    expect(shell.applyGatewayDispatch({ sequence: 41, type: 'MESSAGE_DELETE', payload: {} })).toBe(false)
    expect(shell.applyGatewayDispatch({ sequence: 43, type: 'MESSAGE_CREATE', payload: {} })).toBe(true)
    expect(shell.applyGatewayDispatch({ sequence: 43, type: 'MESSAGE_UPDATE', payload: {} })).toBe(false)
    await nextTick()

    expect(wrapper.find('[data-gateway-sequence="41"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-gateway-sequence="43"]')).toHaveLength(1)
    expect(wrapper.get('[data-gateway-sequence="43"]').text()).toContain('MESSAGE_CREATE')
  })
})
