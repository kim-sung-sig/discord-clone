import { mountSuspended } from '@nuxt/test-utils/runtime'
import { enableAutoUnmount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ChatViewport from '../../components/shell/ChatViewport.vue'
import App from '../../pages/app.vue'
import {
  UNKNOWN_MESSAGE_AUTHOR,
  messageAuthorInitials,
  messageAuthorLabel
} from '../../services/message-author'
import { useAuthStore } from '../../stores/auth'
import { useShellStore } from '../../stores/shell'

enableAutoUnmount(afterEach)

const VIEWER = { id: '6f1c2d3e-0000-4000-8000-000000000001', username: 'viewer_user', displayName: 'Viewer Name' }
const OTHER_ID = '36f8493c-4c80-4500-aa51-f36b44bdcd3f'
const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i

const backendMessage = (overrides: Record<string, unknown>) => ({
  id: 'message-1',
  guildId: 'guild-1',
  channelId: 'channel-general',
  sequence: 1,
  authorId: OTHER_ID,
  content: 'hello',
  mentions: [],
  pinned: false,
  deleted: false,
  edited: false,
  editHistory: [],
  createdAt: '2026-10-24T06:45:00.000Z',
  updatedAt: '2026-10-24T06:45:00.000Z',
  ...overrides
})

// The Nuxt test app restores the session on boot; a late restore failure would reset the shell mid-test.
const settledShell = async () => {
  await mountSuspended(App)
  const auth = useAuthStore()
  await vi.waitFor(() => expect(auth.isRestoring).toBe(false))
  // Shell REST actions only run for the signed-in token; patch both at once so the app's account watcher resets first.
  auth.$patch({ accessToken: 'access-token', user: VIEWER })
  return { auth, shell: useShellStore() }
}

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

describe('messageAuthorLabel', () => {
  it('prefers the server display name and trims it', () => {
    expect(messageAuthorLabel({ authorId: OTHER_ID, authorDisplayName: '  김개발 ' }, VIEWER)).toBe('김개발')
  })

  it('falls back to the signed-in user name for own messages without a display name', () => {
    expect(messageAuthorLabel({ authorId: VIEWER.id, authorDisplayName: null }, VIEWER)).toBe('Viewer Name')
    expect(messageAuthorLabel({ authorId: VIEWER.id }, { ...VIEWER, displayName: ' ' })).toBe('viewer_user')
  })

  it('uses a neutral label instead of the raw id for other authors without a display name', () => {
    expect(messageAuthorLabel({ authorId: OTHER_ID, authorDisplayName: null }, VIEWER)).toBe(UNKNOWN_MESSAGE_AUTHOR)
    expect(messageAuthorLabel({ authorId: OTHER_ID, authorDisplayName: '   ' }, null)).toBe(UNKNOWN_MESSAGE_AUTHOR)
    expect(UNKNOWN_MESSAGE_AUTHOR).toBe('알 수 없는 사용자')
  })

  it('keeps an already confirmed label when a later payload has no display name', () => {
    expect(messageAuthorLabel({ authorId: OTHER_ID }, VIEWER, 'Known Name')).toBe('Known Name')
    expect(messageAuthorLabel({ authorId: OTHER_ID, authorDisplayName: 'Fresh Name' }, VIEWER, 'Known Name')).toBe('Fresh Name')
  })
})

describe('messageAuthorInitials', () => {
  it('derives initials from the displayed name by code point', () => {
    expect(messageAuthorInitials('vibe coder')).toBe('VI')
    expect(messageAuthorInitials('  김개발')).toBe('김개')
    expect(messageAuthorInitials('😀😃 smile')).toBe('😀😃')
    expect(messageAuthorInitials('A')).toBe('A')
    expect(messageAuthorInitials('   ')).toBe('?')
  })
})

describe('shell message author mapping', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    useAuthStore().$reset()
    useShellStore().$reset()
  })

  it('maps REST pages to display names and never shows a raw author id', async () => {
    const { shell } = await settledShell()
    shell.messages = []
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      const url = String(input)
      if (!url.includes('/api/channels/channel-general/messages')) {
        return jsonResponse({ message: 'not stubbed' }, 404)
      }
      if (url.includes('before=')) {
        return jsonResponse({
          messages: [backendMessage({ id: 'message-older', sequence: 1, authorDisplayName: null })],
          nextCursor: null
        })
      }
      return jsonResponse({
        messages: [
          backendMessage({ id: 'message-named', sequence: 3, authorDisplayName: '김개발' }),
          backendMessage({ id: 'message-own', sequence: 2, authorId: VIEWER.id })
        ],
        nextCursor: 'cursor-1'
      })
    })

    await shell.resyncChannelMessages('channel-general', 'access-token')
    await shell.loadOlderChannelMessages('channel-general', 'access-token')

    const authorOf = (id: string) => shell.messages.find((message) => message.id === id)?.author
    expect(authorOf('message-named')).toBe('김개발')
    expect(authorOf('message-own')).toBe('Viewer Name')
    expect(authorOf('message-older')).toBe(UNKNOWN_MESSAGE_AUTHOR)
    expect(shell.messages.map((message) => message.author).join(' ')).not.toMatch(UUID_PATTERN)
  })

  it('maps the confirmed create response to the author display name', async () => {
    const { shell } = await settledShell()
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => String(input).includes('/api/channels/channel-general/messages')
      ? jsonResponse(backendMessage({
        id: 'message-created',
        authorId: VIEWER.id,
        authorDisplayName: 'Server Name',
        content: 'created'
      }))
      : jsonResponse({ message: 'not stubbed' }, 404))

    const created = await shell.sendBackendMessage('channel-general', 'created', 'access-token')

    expect(created?.author).toBe('Server Name')
    expect(shell.messages.find((message) => message.id === 'message-created')?.author).toBe('Server Name')
  })

  it('maps gateway message creation to the payload display name or a safe fallback', async () => {
    const { shell } = await settledShell()
    const authorOf = (id: string) => shell.messages.find((message) => message.id === id)?.author
    const create = (payload: Record<string, unknown>) => shell.applyGatewayDispatch({
      sequence: shell.gateway.lastSequence + 1,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: { channelId: 'channel-general', content: 'gateway body', ...payload }
    })

    expect(create({ id: 'gateway-named', authorId: OTHER_ID, authorDisplayName: '실시간 작성자' })).toBe(true)
    expect(authorOf('gateway-named')).toBe('실시간 작성자')
    expect(create({ id: 'gateway-unknown', authorId: OTHER_ID })).toBe(true)
    expect(authorOf('gateway-unknown')).toBe(UNKNOWN_MESSAGE_AUTHOR)
    expect(create({ id: 'gateway-own', authorId: VIEWER.id })).toBe(true)
    expect(authorOf('gateway-own')).toBe('Viewer Name')
  })

  it('keeps a confirmed author name when a gateway echo omits the display name', async () => {
    const { shell } = await settledShell()
    shell.messages = [{
      id: 'message-confirmed',
      channelId: 'channel-general',
      sequence: 1,
      author: 'Confirmed Name',
      body: 'hello',
      createdAt: '2026-10-24T06:45:00.000Z',
      edited: false,
      pinned: false,
      deleted: false,
      mentions: [],
      attachments: [],
      status: 'sent'
    }]

    expect(shell.applyGatewayDispatch({
      sequence: shell.gateway.lastSequence + 1,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: { id: 'message-confirmed', channelId: 'channel-general', authorId: OTHER_ID, content: 'hello' }
    })).toBe(true)

    expect(shell.messages.find((message) => message.id === 'message-confirmed')?.author).toBe('Confirmed Name')
  })

  it('does not borrow another message author for a new gateway message without a display name', async () => {
    const { shell } = await settledShell()
    shell.messages = [{
      id: 'message-unrelated',
      channelId: 'channel-general',
      sequence: 1,
      author: 'Unrelated Name',
      body: 'hello',
      createdAt: '2026-10-24T06:45:00.000Z',
      edited: false,
      pinned: false,
      deleted: false,
      mentions: [],
      attachments: [],
      status: 'sent'
    }]

    expect(shell.applyGatewayDispatch({
      sequence: shell.gateway.lastSequence + 1,
      type: 'MESSAGE_CREATE',
      channelId: 'channel-general',
      payload: { id: 'message-new', channelId: 'channel-general', authorId: OTHER_ID, content: 'new' }
    })).toBe(true)

    expect(shell.messages.find((message) => message.id === 'message-new')?.author).toBe(UNKNOWN_MESSAGE_AUTHOR)
  })

  it('renders the author name and avatar initials from the displayed name', async () => {
    const shell = useShellStore()
    shell.activeChannelId = 'channel-general'
    shell.messages = [{
      id: 'message-rendered',
      channelId: 'channel-general',
      sequence: 1,
      author: '김개발',
      body: 'rendered body',
      createdAt: '2026-10-24T06:45:00.000Z',
      edited: false,
      pinned: false,
      deleted: false,
      mentions: [],
      attachments: [],
      status: 'sent'
    }]

    const wrapper = await mountSuspended(ChatViewport)
    const message = wrapper.get('[data-message-id="message-rendered"]')

    expect(message.get('.avatar').text()).toBe('김개')
    expect(message.get('.message-meta strong').text()).toBe('김개발')
  })
})
