import { mountSuspended } from '@nuxt/test-utils/runtime'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../../pages/app.vue'
import { discordApiPaths, createDiscordRestClient } from '../../services/discord-api'
import {
  createGatewaySocketLifecycle,
  isGatewayDispatch,
  normalizeGatewayDispatch,
  toShellGatewayEvent
} from '../../services/gateway-client'
import { useShellStore } from '../../stores/shell'

describe('Discord shell API and Gateway contracts', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('builds stable REST paths for auth, guild, channel, invite, and gateway calls', () => {
    expect(discordApiPaths.authLogin()).toBe('/api/auth/login')
    expect(discordApiPaths.auth.refresh()).toBe('/api/auth/refresh')
    expect(discordApiPaths.auth.sessions()).toBe('/api/auth/sessions')
    expect(discordApiPaths.auth.session('session-1')).toBe('/api/auth/sessions/session-1')
    expect(discordApiPaths.guild('guild-1')).toBe('/api/guilds/guild-1')
    expect(discordApiPaths.guild.messageReport('guild-1', 'channel-1', 'message-1')).toBe(
      '/api/guilds/guild-1/channels/channel-1/messages/message-1/reports'
    )
    expect(discordApiPaths.guild.messageReports('guild-1', { limit: 25 })).toBe(
      '/api/guilds/guild-1/message-reports?limit=25'
    )
    expect(discordApiPaths.guild.resolveMessageReport('guild-1', 'report-1')).toBe(
      '/api/guilds/guild-1/message-reports/report-1/resolve'
    )
    expect(discordApiPaths.channelMessages('channel-1', { before: 'msg-9', limit: 25 })).toBe(
      '/api/channels/channel-1/messages?before=msg-9&limit=25'
    )
    expect(discordApiPaths.invitePreview('abc123')).toBe('/api/invites/abc123')
    expect(discordApiPaths.gatewayEvents('session-1', 42)).toBe('/api/gateway/sessions/session-1/events?afterSeq=42')
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
    expect(calls[0]?.init?.credentials).toBe('include')
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

  it('normalizes real WebSocket EVENT frames to their domain event type', () => {
    const dispatch = normalizeGatewayDispatch({
      type: 'EVENT',
      sequence: 43,
      eventType: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-ws-1',
        content: 'hello from websocket'
      },
      createdAt: '2026-05-16T00:00:00.000Z'
    })

    expect(dispatch).toMatchObject({
      sequence: 43,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-ws-1',
        content: 'hello from websocket'
      }
    })
  })

  it('connects gateway socket with identify, heartbeat, and dispatch routing', () => {
    const sockets: FakeGatewaySocket[] = []
    const dispatches: unknown[] = []
    const lifecycle = createGatewaySocketLifecycle({
      url: 'ws://localhost/ws/gateway',
      accessToken: 'access-token',
      lastSequence: () => 42,
      heartbeatIntervalMs: 0,
      webSocketFactory: (url) => {
        const socket = new FakeGatewaySocket(url)
        sockets.push(socket)
        return socket
      },
      onDispatch: (dispatch) => dispatches.push(dispatch)
    })

    lifecycle.connect()
    sockets[0]?.open()
    lifecycle.sendHeartbeat()
    sockets[0]?.message(JSON.stringify({
      sequence: 43,
      type: 'EVENT',
      eventType: 'MESSAGE_CREATE',
      payload: { id: 'message-ws' }
    }))
    sockets[0]?.message(JSON.stringify({ sequence: 0, type: '', payload: {} }))

    expect(sockets[0]?.sentFrames()).toContainEqual({
      op: 'IDENTIFY',
      token: 'access-token'
    })
    expect(sockets[0]?.sentFrames()).toContainEqual({
      op: 'HEARTBEAT',
      lastSequence: 42
    })
    expect(dispatches).toHaveLength(1)
    expect(dispatches[0]).toMatchObject({ sequence: 43, type: 'MESSAGE_CREATE' })
  })

  it('resumes gateway socket when session id and last sequence exist', () => {
    const sockets: FakeGatewaySocket[] = []
    const lifecycle = createGatewaySocketLifecycle({
      url: 'ws://localhost/ws/gateway',
      accessToken: 'access-token',
      sessionId: () => 'session-1',
      lastSequence: () => 99,
      heartbeatIntervalMs: 0,
      webSocketFactory: (url) => {
        const socket = new FakeGatewaySocket(url)
        sockets.push(socket)
        return socket
      },
      onDispatch: () => undefined
    })

    lifecycle.connect()
    sockets[0]?.open()

    expect(sockets[0]?.sentFrames()[0]).toEqual({
      op: 'RESUME',
      token: 'access-token',
      sessionId: 'session-1',
      lastSequence: 99
    })
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

  it('reconciles REST message success with the matching Gateway echo without duplicating state', async () => {
    await mountSuspended(App)
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    const shell = useShellStore()
    shell.$reset()

    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      return new Response(JSON.stringify({
        id: 'message-reconciled',
        guildId: shell.guild.id,
        channelId: 'channel-general',
        authorId: shell.currentUser,
        content: 'backend echo once',
        mentions: [],
        pinned: false,
        deleted: false,
        edited: false,
        editHistory: [],
        createdAt: '2026-05-16T00:00:00.000Z',
        updatedAt: '2026-05-16T00:00:00.000Z'
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    })

    await shell.sendBackendMessage('channel-general', 'backend echo once', 'access-token')
    const body = JSON.parse(String(calls[0]?.init?.body))
    expect(body.clientEventId).toMatch(/^web-shell:/)
    expect(body.idempotencyKey).toBe(body.clientEventId)
    expect(shell.pendingMutations).toHaveLength(0)

    expect(shell.applyGatewayDispatch({
      type: 'EVENT',
      sequence: 43,
      eventType: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-reconciled',
        channelId: 'channel-general',
        authorId: shell.currentUser,
        content: 'backend echo once',
        clientEventId: body.clientEventId,
        requestId: shell.apiLastRequestId,
        createdAt: '2026-05-16T00:00:00.000Z'
      },
      createdAt: '2026-05-16T00:00:00.100Z'
    })).toBe(true)

    expect(shell.messages.filter((message) => message.body === 'backend echo once')).toHaveLength(1)
    expect(shell.messages.find((message) => message.id === 'message-reconciled')?.status).toBe('sent')
    expect(shell.failedMutations).toHaveLength(0)
  })

  it('rolls back failed optimistic message mutations and keeps an accessible error', async () => {
    await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()
    vi.stubGlobal('fetch', async () =>
      new Response(JSON.stringify({ message: 'rate limited' }), {
        status: 429,
        headers: { 'content-type': 'application/json' }
      }))

    await expect(shell.sendBackendMessage('channel-general', 'optimistic rollback', 'access-token')).resolves.toBeNull()

    expect(shell.messages.some((message) => message.body === 'optimistic rollback')).toBe(false)
    expect(shell.pendingMutations).toHaveLength(0)
    expect(shell.failedMutations).toHaveLength(1)
    expect(shell.failedMutations[0]?.clientEventId).toMatch(/^web-shell:/)
    expect(shell.apiError).toContain('Request id:')
  })

  it('detects gateway sequence gaps and refuses stale message updates', async () => {
    await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()

    expect(shell.applyGatewayDispatch({
      sequence: 45,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-gap-skipped',
        channelId: 'channel-general',
        authorId: 'cto-bot',
        content: 'should wait for resync',
        createdAt: '2026-05-16T00:00:00.000Z'
      }
    })).toBe(false)
    expect(shell.resyncRequired).toBe(true)
    expect(shell.messages.some((message) => message.id === 'message-gap-skipped')).toBe(false)

    expect(shell.applyGatewayDispatch({
      sequence: 43,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-versioned',
        channelId: 'channel-general',
        authorId: 'cto-bot',
        content: 'new version',
        serverVersion: 2,
        createdAt: '2026-05-16T00:00:00.000Z'
      }
    })).toBe(true)
    expect(shell.applyGatewayDispatch({
      sequence: 44,
      type: 'MESSAGE_UPDATE',
      channelId: 'channel-general',
      payload: {
        id: 'message-versioned',
        content: 'old version',
        serverVersion: 1
      }
    })).toBe(true)

    expect(shell.messages.find((message) => message.id === 'message-versioned')?.body).toBe('new version')
  })

  it('clears realtime resync state after a bounded channel message refetch', async () => {
    await mountSuspended(App)
    const shell = useShellStore()
    shell.$reset()
    shell.resyncRequired = true
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ input, init })
      return new Response(JSON.stringify({
        messages: [
          {
            id: 'message-resynced',
            guildId: shell.guild.id,
            channelId: 'channel-general',
            authorId: 'cto-bot',
            content: 'resynced snapshot',
            mentions: [],
            pinned: false,
            deleted: false,
            edited: false,
            editHistory: [],
            createdAt: '2026-05-16T00:00:00.000Z',
            updatedAt: '2026-05-16T00:00:00.000Z'
          }
        ],
        nextCursor: null
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    })

    await expect(shell.resyncChannelMessages('channel-general', 'access-token', 25)).resolves.toHaveLength(1)

    expect(String(calls[0]?.input)).toContain('/api/channels/channel-general/messages?limit=25')
    expect(shell.resyncRequired).toBe(false)
    expect(shell.messages.find((message) => message.id === 'message-resynced')?.body).toBe('resynced snapshot')
  })
})

class FakeGatewaySocket {
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  private readonly sent: unknown[] = []

  constructor(readonly url: string) {}

  send(frame: string) {
    this.sent.push(JSON.parse(frame))
  }

  close() {
    this.onclose?.()
  }

  open() {
    this.onopen?.()
  }

  message(data: string) {
    this.onmessage?.({ data })
  }

  sentFrames() {
    return this.sent
  }
}
