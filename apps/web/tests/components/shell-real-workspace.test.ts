import { mountSuspended } from '@nuxt/test-utils/runtime'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createSSRApp, nextTick, type Component } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { getActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../../pages/app.vue'
import ChatViewport from '../../components/shell/ChatViewport.vue'
import DmSidebar from '../../components/social/DmSidebar.vue'
import GatewayStatusPanel from '../../components/shell/GatewayStatusPanel.vue'
import MemberSidebar from '../../components/shell/MemberSidebar.vue'
import RolePermissionPanel from '../../components/shell/RolePermissionPanel.vue'
import UserPanel from '../../components/shell/UserPanel.vue'
import VoicePanel from '../../components/shell/VoicePanel.vue'
import { useAuthStore } from '../../stores/auth'
import { compareShellMessages, useShellStore, type ShellMessage } from '../../stores/shell'
import { seedShellDemoState } from '../fixtures/shell-demo-state'

enableAutoUnmount(afterEach)

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' }
  })

// `voice.tokenProvider` keeps its single literal type value; VoicePanel no longer renders it.
const demoMarkers = ['vibe-coder', 'cto-bot', 'spam-drone', 'qa-scout', 'gateway-session-t05', 'T07 strike team', 'Skeleton entitlement']

const shellMessage = (overrides: Partial<ShellMessage> & Pick<ShellMessage, 'id' | 'sequence'>): ShellMessage => ({
  channelId: 'channel-a',
  author: 'user-b',
  body: overrides.id,
  createdAt: '2026-10-01T10:00:00.000Z',
  edited: false,
  pinned: false,
  deleted: false,
  mentions: [],
  attachments: [],
  ...overrides
})

const backendMessage = (id: string, sequence: number | null, createdAt: string) => ({
  id,
  channelId: 'channel-a',
  sequence,
  authorId: 'user-b',
  content: id,
  mentions: [],
  pinned: false,
  deleted: false,
  edited: false,
  createdAt
})

const signIn = () => {
  const auth = useAuthStore()
  auth.$patch({ accessToken: 'token-a', user: { id: 'user-a', username: 'kim', displayName: '김성식' } })
  return auth
}

const openBackendChannel = () => {
  const shell = useShellStore()
  shell.$patch({
    backendWorkspace: true,
    guild: { id: 'guild-a', name: 'Guild A' },
    activeChannelId: 'channel-a',
    channelGroups: [
      { id: 'text-channels', name: 'Text Channels', channels: [
        { id: 'channel-a', name: 'alpha', type: 'GUILD_TEXT' },
        { id: 'channel-b', name: 'beta', type: 'GUILD_TEXT' }
      ] }
    ]
  })
  return shell
}

const activeIds = () => useShellStore().activeMessages.map((message) => message.id)

// A render pass without mount hooks: what SSR produces and what hydration compares against.
const renderFirstPass = (component: Component) => {
  const app = createSSRApp(component)
  app.use(getActivePinia()!)
  return renderToString(app)
}

describe('real workspace data', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    useAuthStore().$reset()
    useShellStore().$reset()
  })

  describe('default state', () => {
    it('starts a real session without demo fixtures', () => {
      const shell = useShellStore()
      shell.$reset()

      expect(shell.currentUser).toBe('')
      expect(shell.guild).toEqual({ id: '', name: '' })
      expect(shell.activeChannelId).toBe('')
      expect(shell.channelGroups).toEqual([])
      expect(shell.messages).toEqual([])
      expect(shell.members).toEqual([])
      expect(shell.roles).toEqual([])
      expect(shell.permissionOverwrites).toEqual([])
      expect(shell.adminConsole.permissionDraft).toBeNull()
      expect(shell.invitePreview.code).toBe('')
      expect(shell.invitePreview.roleGrantIds).toEqual([])
      expect(shell.gateway).toMatchObject({ status: 'DISCONNECTED', sessionId: '', lastSequence: 0, resumed: false, events: [] })
      expect(shell.latestGatewaySequence).toBe(0)
      expect(shell.processedEventIds).toEqual([])
      expect(shell.social.friends).toEqual([])
      expect(shell.social.directMessages).toEqual([])
      expect(shell.social.groupDms).toEqual([])
      expect(shell.forum.forums).toEqual([])
      expect(shell.forum.threads).toEqual([])
      expect(shell.moderation.automodRules).toEqual([])
      expect(shell.moderation.auditLogs).toEqual([])
      expect(shell.moderation.onboardingQuestion.answers).toEqual([])
      expect(shell.voice.participants).toEqual([])
      expect(shell.voice.events).toEqual([])
      expect(shell.experience.catalog).toEqual([])
      expect(shell.experience.quests).toEqual([])
      expect(shell.presence.users).toEqual({})
      expect(shell.presence.typingByChannel).toEqual({})
      expect(shell.presence.readMarkers).toEqual({})
      const serialized = JSON.stringify(shell.$state)
      for (const marker of demoMarkers) {
        expect(serialized).not.toContain(marker)
      }
    })

    it('resets an account back to the clean default state', () => {
      const shell = useShellStore()
      shell.$reset()
      const clean = JSON.parse(JSON.stringify(shell.$state))
      seedShellDemoState(shell)
      shell.accountGeneration = 4

      shell.resetAccountState()

      expect(shell.accountGeneration).toBe(5)
      expect(JSON.parse(JSON.stringify({ ...shell.$state, accountGeneration: 0 }))).toEqual(clean)
    })

    it('renders the same empty shell before and after the client session restore', async () => {
      useShellStore().$reset()
      vi.stubGlobal('fetch', async () => jsonResponse({ message: 'unauthorized' }, 401))
      const wrapper = await mountSuspended(App)
      const auth = useAuthStore()
      await vi.waitFor(() => expect(auth.isRestoring).toBe(false))

      expect(wrapper.get('[data-testid="active-channel"]').text()).not.toContain('# general')
      expect(wrapper.find('[data-testid="channel-general"]').exists()).toBe(false)
      const before = {
        chat: wrapper.get('[data-testid="chat-viewport"]').html(),
        channels: wrapper.get('[data-testid="channel-sidebar"]').html()
      }

      auth.acceptSession({ accessToken: 'token-a', user: { id: 'user-a', username: 'kim', displayName: 'Kim' } })
      await nextTick()

      expect(wrapper.get('[data-testid="chat-viewport"]').html()).toBe(before.chat)
      expect(wrapper.get('[data-testid="channel-sidebar"]').html()).toBe(before.channels)
    })
  })

  describe('hydration', () => {
    it('renders auth-dependent parts identically before and after the client session restore', async () => {
      const shell = openBackendChannel()
      shell.messagePageCursors = { 'channel-a': 'cursor-1' }
      const components = [UserPanel, MemberSidebar, ChatViewport]

      const serverHtml = await Promise.all(components.map(renderFirstPass))
      signIn()
      const firstClientHtml = await Promise.all(components.map(renderFirstPass))

      expect(firstClientHtml).toEqual(serverHtml)
      expect(firstClientHtml[0]).not.toContain('김성식')
      expect(firstClientHtml[0]).not.toContain('session-panel')
      expect(firstClientHtml[2]).not.toContain('load-older-messages')
    })

    it('shows the signed-in identity and sessions in the user panel once mounted', async () => {
      useShellStore().$reset()
      signIn()

      const wrapper = await mountSuspended(UserPanel)

      expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('김성식')
      expect(wrapper.find('[data-testid="session-panel"]').exists()).toBe(true)
    })
  })

  describe('panels without demo data', () => {
    it('shows the signed-in user when no member list is available', async () => {
      useShellStore().$reset()
      signIn()

      const wrapper = await mountSuspended(MemberSidebar)

      expect(wrapper.get('[data-testid="member-self"]').text()).toContain('김성식')
      expect(wrapper.get('[data-testid="member-self"]').text()).toContain('Online')
      expect(wrapper.text()).not.toContain('vibe-coder')
    })

    it('keeps rendering a loaded member list instead of the signed-in fallback', async () => {
      seedShellDemoState()
      signIn()

      const wrapper = await mountSuspended(MemberSidebar)

      expect(wrapper.text()).toContain('vibe-coder')
      expect(wrapper.find('[data-testid="member-self"]').exists()).toBe(false)
    })

    it('drops the hard-coded qa-scout action and hides empty DM sections', async () => {
      seedShellDemoState()
      const seeded = await mountSuspended(DmSidebar)

      expect(seeded.get('[data-testid="group-dm-members"]').text()).toContain('vibe-coder')
      expect(seeded.find('[data-testid="add-group-member"]').exists()).toBe(false)
      expect(seeded.text()).not.toContain('qa-scout')
      seeded.unmount()

      useShellStore().$reset()
      const empty = await mountSuspended(DmSidebar)

      expect(empty.find('#direct-message-title').exists()).toBe(false)
      expect(empty.find('#group-message-title').exists()).toBe(false)
      expect(empty.find('[data-testid="group-dm-members"]').exists()).toBe(false)
    })

    it('shows the real disconnected gateway state without a fake session or sequence', async () => {
      useShellStore().$reset()

      const wrapper = await mountSuspended(GatewayStatusPanel)

      expect(wrapper.get('[data-testid="gateway-status"]').text()).toBe('DISCONNECTED')
      expect(wrapper.find('[data-testid="gateway-last-sequence"]').exists()).toBe(false)
      expect(wrapper.find('[data-gateway-sequence]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('gateway-session-t05')
      expect(wrapper.text()).not.toContain('42')
    })

    it('shows no fake roles or privileged audit section', async () => {
      useShellStore().$reset()

      const wrapper = await mountSuspended(RolePermissionPanel)

      expect(wrapper.findAll('.role-card')).toHaveLength(0)
      expect(wrapper.find('[data-testid="preview-as-role"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="permission-diff"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('Privileged audit')
      expect(wrapper.text()).not.toContain('Member roles')
      expect(wrapper.text()).not.toContain('Active channel overwrites')
      expect(wrapper.text()).not.toContain('Moderator')
    })

    it('does not show the voice provider enum line', async () => {
      useShellStore().$reset()

      const wrapper = await mountSuspended(VoicePanel)

      expect(wrapper.find('[data-testid="voice-token-provider"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('LIVEKIT_SKELETON')
      expect(wrapper.text()).not.toContain('Provider')
      expect(wrapper.find('[data-testid="voice-events"]').exists()).toBe(false)
    })
  })

  describe('message order', () => {
    it('orders confirmed messages by server sequence and keeps pending local messages last', () => {
      const pending = shellMessage({ id: 'pending', sequence: 99, status: 'sending', localSequence: true, createdAt: '2026-10-01T09:00:00.000Z' })
      const late = shellMessage({ id: 'late', sequence: 2, createdAt: '2026-10-01T08:00:00.000Z' })
      const early = shellMessage({ id: 'early', sequence: 1, createdAt: '2026-10-01T11:00:00.000Z' })
      const local = shellMessage({ id: 'local', sequence: 50, localSequence: true, createdAt: '2026-10-01T07:00:00.000Z' })

      expect([pending, late, early].sort(compareShellMessages).map((message) => message.id)).toEqual(['early', 'late', 'pending'])
      expect([early, local].sort(compareShellMessages).map((message) => message.id)).toEqual(['local', 'early'])
    })

    it('shows loaded pages and older pages oldest first', async () => {
      signIn()
      const shell = openBackendChannel()
      vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
        const path = String(input)
        if (path.includes('before=cursor-1')) {
          return jsonResponse({ messages: [
            backendMessage('m2', 2, '2026-10-01T10:02:00.000Z'),
            backendMessage('m1', 1, '2026-10-01T10:01:00.000Z')
          ], nextCursor: null })
        }
        return jsonResponse({ messages: [
          backendMessage('m5', 5, '2026-10-01T10:05:00.000Z'),
          backendMessage('m4', 4, '2026-10-01T10:04:00.000Z'),
          backendMessage('m3', 3, '2026-10-01T10:03:00.000Z')
        ], nextCursor: 'cursor-1' })
      })

      await shell.resyncChannelMessages('channel-a', 'token-a')
      expect(activeIds()).toEqual(['m3', 'm4', 'm5'])

      await shell.loadOlderChannelMessages('channel-a', 'token-a')
      expect(activeIds()).toEqual(['m1', 'm2', 'm3', 'm4', 'm5'])
    })

    it('orders older pages without server sequences by creation time', async () => {
      signIn()
      const shell = openBackendChannel()
      shell.messages = [shellMessage({ id: 'm3', sequence: 3, createdAt: '2026-10-01T10:03:00.000Z' })]
      shell.messagePageCursors = { 'channel-a': 'cursor-1' }
      vi.stubGlobal('fetch', async () => jsonResponse({ messages: [
        backendMessage('m2', null, '2026-10-01T10:02:00.000Z'),
        backendMessage('m1', null, '2026-10-01T10:01:00.000Z')
      ], nextCursor: null }))

      await shell.loadOlderChannelMessages('channel-a', 'token-a')

      expect(activeIds()).toEqual(['m1', 'm2', 'm3'])
      expect(shell.messages.find((message) => message.id === 'm1')?.localSequence).toBe(true)
    })

    it('keeps sent messages at the bottom while pending and places them by sequence once confirmed', async () => {
      signIn()
      const shell = openBackendChannel()
      shell.messages = [
        shellMessage({ id: 'm2', sequence: 2, createdAt: '2026-10-01T10:02:00.000Z' }),
        shellMessage({ id: 'm1', sequence: 1, createdAt: '2026-10-01T10:01:00.000Z' })
      ]
      let resolvePost!: (response: Response) => void
      vi.stubGlobal('fetch', () => new Promise<Response>((resolve) => { resolvePost = resolve }))

      const sent = shell.sendBackendMessage('channel-a', 'hello', 'token-a')
      await vi.waitFor(() => expect(resolvePost).toBeDefined())
      expect(activeIds().at(-1)).toMatch(/^local-/)
      expect(shell.activeMessages.at(-1)?.status).toBe('sending')

      shell.applyGatewayDispatch({
        sequence: 1, type: 'MESSAGE_CREATE', channelId: 'channel-a', createdAt: '2026-10-01T10:03:00.000Z',
        payload: { id: 'm3', messageSequence: 3, authorId: 'user-b', content: 'from gateway', createdAt: '2026-10-01T10:03:00.000Z' }
      })
      expect(activeIds().slice(0, 3)).toEqual(['m1', 'm2', 'm3'])
      expect(activeIds().at(-1)).toMatch(/^local-/)

      resolvePost(jsonResponse({ ...backendMessage('m4', 4, '2026-10-01T10:04:00.000Z'), authorId: 'user-a', content: 'hello' }, 201))
      await sent

      expect(activeIds()).toEqual(['m1', 'm2', 'm3', 'm4'])
      expect(shell.messages.find((message) => message.id === 'm4')?.localSequence).toBeUndefined()
    })

    it('places gateway messages without a server sequence by creation time', () => {
      const shell = openBackendChannel()
      shell.messages = [shellMessage({ id: 'm2', sequence: 2, createdAt: '2026-10-01T10:02:00.000Z' })]

      shell.applyGatewayDispatch({
        sequence: 1, type: 'MESSAGE_CREATE', channelId: 'channel-a', createdAt: '2026-10-01T10:01:00.000Z',
        payload: { id: 'm1', authorId: 'user-b', content: 'late echo', createdAt: '2026-10-01T10:01:00.000Z' }
      })

      expect(activeIds()).toEqual(['m1', 'm2'])
      expect(shell.messages.find((message) => message.id === 'm1')?.localSequence).toBe(true)
    })
  })

  describe('message list scroll', () => {
    const stubListSize = (element: HTMLElement) => {
      Object.defineProperty(element, 'scrollHeight', { configurable: true, get: () => 900 })
      Object.defineProperty(element, 'clientHeight', { configurable: true, get: () => 300 })
    }

    it('scrolls to the newest message when the channel changes or the user sends', async () => {
      const shell = openBackendChannel()
      shell.messages = [
        shellMessage({ id: 'a1', sequence: 1 }),
        shellMessage({ id: 'b1', channelId: 'channel-b', sequence: 2 })
      ]
      const wrapper = await mountSuspended(ChatViewport)
      const list = wrapper.get('[data-testid="message-list"]').element as HTMLElement
      stubListSize(list)

      list.scrollTop = 0
      list.dispatchEvent(new Event('scroll'))
      shell.activeChannelId = 'channel-b'
      await flushPromises()
      expect(list.scrollTop).toBe(900)

      list.scrollTop = 0
      list.dispatchEvent(new Event('scroll'))
      shell.messages = [...shell.messages, shellMessage({ id: 'b0', channelId: 'channel-b', sequence: 0, createdAt: '2026-09-30T10:00:00.000Z' })]
      await flushPromises()
      expect(list.scrollTop).toBe(0)

      await wrapper.get('[data-testid="message-input"]').setValue('local hello')
      await wrapper.get('[data-testid="message-composer"]').trigger('submit')
      await flushPromises()
      expect(activeIds().at(-1)).toMatch(/^message-/)
      expect(list.scrollTop).toBe(900)
    })
  })

  describe('older page scroll', () => {
    it('keeps the reader position when an older page is prepended', async () => {
      signIn()
      const shell = openBackendChannel()
      shell.messages = [shellMessage({ id: 'm3', sequence: 3, createdAt: '2026-10-01T10:03:00.000Z' })]
      shell.messagePageCursors = { 'channel-a': 'cursor-1' }
      vi.stubGlobal('fetch', async () => jsonResponse({ messages: [
        backendMessage('m2', 2, '2026-10-01T10:02:00.000Z'),
        backendMessage('m1', 1, '2026-10-01T10:01:00.000Z')
      ], nextCursor: null }))
      const wrapper = await mountSuspended(ChatViewport)
      const list = wrapper.get('[data-testid="message-list"]').element as HTMLElement
      Object.defineProperty(list, 'scrollHeight', { configurable: true, get: () => shell.messages.length * 200 })
      Object.defineProperty(list, 'clientHeight', { configurable: true, get: () => 100 })
      list.scrollTop = 40
      list.dispatchEvent(new Event('scroll'))

      await wrapper.get('[data-testid="load-older-messages"]').trigger('click')
      await flushPromises()

      expect(activeIds()).toEqual(['m1', 'm2', 'm3'])
      expect(list.scrollTop).toBe(440)
    })
  })

  describe('read marker', () => {
    it('selects only the newest server-confirmed message of the active channel', () => {
      const shell = openBackendChannel()
      shell.messages = [
        shellMessage({ id: 'confirmed', sequence: 5, status: 'sent' }),
        shellMessage({ id: 'optimistic', sequence: 6, status: 'sending', localSequence: true }),
        shellMessage({ id: 'failed', sequence: 7, status: 'failed', localSequence: true }),
        shellMessage({ id: 'local', sequence: 8, localSequence: true }),
        shellMessage({ id: 'other-channel', channelId: 'channel-b', sequence: 9 })
      ]

      expect(shell.activeReadMarkerMessage?.id).toBe('confirmed')

      shell.messages = [shellMessage({ id: 'optimistic', sequence: 1, status: 'sending', localSequence: true })]
      expect(shell.activeReadMarkerMessage).toBeNull()
    })

    it('never sends a pending optimistic local sequence to the read marker endpoint', async () => {
      const observers: Array<{ notify: () => void }> = []
      vi.stubGlobal('IntersectionObserver', class {
        constructor(callback: IntersectionObserverCallback) {
          observers.push({ notify: () => { void callback([{ isIntersecting: true } as IntersectionObserverEntry], this as IntersectionObserver) } })
        }
        observe() {}
        disconnect() {}
      })
      const readBodies: unknown[] = []
      vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input)
        if (path.endsWith('/api/presence/channels/channel-a/read') && init?.method === 'PUT') {
          readBodies.push(JSON.parse(String(init.body)))
          return jsonResponse({ lastReadSequence: 5 })
        }
        if (path.endsWith('/api/notifications')) return jsonResponse([])
        if (path.endsWith('/api/notifications/unread-count')) return jsonResponse({ unreadCount: 0 })
        if (path.endsWith('/api/notifications/channel-unread')) return jsonResponse({})
        return jsonResponse({}, 404)
      })
      signIn()
      const shell = openBackendChannel()
      await mountSuspended(ChatViewport)
      shell.messages = [
        shellMessage({ id: 'confirmed', sequence: 5, status: 'sent' }),
        shellMessage({ id: 'optimistic', sequence: 6, author: 'user-a', status: 'sending', localSequence: true, createdAt: '2026-10-01T10:05:00.000Z' })
      ]

      await vi.waitFor(() => expect(observers.length).toBeGreaterThan(0))
      observers.at(-1)!.notify()

      await vi.waitFor(() => expect(shell.presence.readMarkers['channel-a']?.lastReadSequence).toBe(5))
      expect(readBodies).toEqual([{ lastReadSequence: 5 }])
    })

    it('marks read when a message with the same id and sequence becomes server-confirmed', async () => {
      const observers: Array<{ notify: () => void }> = []
      vi.stubGlobal('IntersectionObserver', class {
        constructor(callback: IntersectionObserverCallback) {
          observers.push({ notify: () => { void callback([{ isIntersecting: true } as IntersectionObserverEntry], this as IntersectionObserver) } })
        }
        observe() {}
        disconnect() {}
      })
      const readBodies: unknown[] = []
      vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input)
        if (path.endsWith('/api/presence/channels/channel-a/read') && init?.method === 'PUT') {
          readBodies.push(JSON.parse(String(init.body)))
          return jsonResponse({ lastReadSequence: 4 })
        }
        if (path.endsWith('/api/notifications')) return jsonResponse([])
        if (path.endsWith('/api/notifications/unread-count')) return jsonResponse({ unreadCount: 0 })
        if (path.endsWith('/api/notifications/channel-unread')) return jsonResponse({})
        return jsonResponse({}, 404)
      })
      signIn()
      const shell = openBackendChannel()
      await mountSuspended(ChatViewport)
      shell.messages = [shellMessage({ id: 'm4', sequence: 4, status: 'sent', localSequence: true })]
      await flushPromises()
      expect(observers).toHaveLength(0)

      shell.messages = [shellMessage({ id: 'm4', sequence: 4, status: 'sent' })]
      await vi.waitFor(() => expect(observers.length).toBeGreaterThan(0))
      observers.at(-1)!.notify()

      await vi.waitFor(() => expect(readBodies).toEqual([{ lastReadSequence: 4 }]))
    })
  })
})
