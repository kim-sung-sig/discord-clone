<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ServerRail from '../components/shell/ServerRail.vue'
import ChannelSidebar from '../components/shell/ChannelSidebar.vue'
import ChatViewport from '../components/shell/ChatViewport.vue'
import VoicePanel from '../components/shell/VoicePanel.vue'
import MemberSidebar from '../components/shell/MemberSidebar.vue'
import GatewayStatusPanel from '../components/shell/GatewayStatusPanel.vue'
import UserPanel from '../components/shell/UserPanel.vue'
import { useAuthStore } from '../stores/auth'
import { usePreferencesStore, type LocaleId, type ThemeId } from '../stores/preferences'
import { useShellStore, type BackendNotification } from '../stores/shell'

const auth = useAuthStore()
const shell = useShellStore()
const preferences = usePreferencesStore()

type MobilePane = 'channels' | 'chat' | 'members' | 'voice'
type WorkbenchView = 'explorer' | 'search' | 'inbox' | 'calls' | 'settings'

const activeMobilePane = ref<MobilePane>('chat')
const activeWorkbenchView = ref<WorkbenchView>('explorer')
const bottomPanelOpen = ref(false)
const searchQuery = ref('')
const backendDmDraft = ref('')
const backendDmSection = ref<HTMLElement | null>(null)
const inboxHasMore = ref(false)
const inboxLoadingMore = ref(false)
let backendDmReadObserver: IntersectionObserver | null = null
let backendDmReadGeneration = 0
const browserNotificationsEnabled = ref(false)
const browserNotificationPermission = ref<NotificationPermission | 'unsupported'>('unsupported')
const seenNotificationIds = new Set<string>()
let notificationPollTimer: number | null = null
let notificationPollingGeneration = 0
let notificationPollingPending = false
let notificationWatermark = 0
let mounted = true
const mobileShellReady = ref(false)
const platformSurface = ref<'web-desktop' | 'pwa-mobile'>('web-desktop')
const mobilePanes = computed<Array<{ id: MobilePane, label: string }>>(() => [
  { id: 'channels', label: preferences.t('mobile.channels') },
  { id: 'chat', label: preferences.t('mobile.chat') },
  { id: 'members', label: preferences.t('mobile.members') },
  { id: 'voice', label: preferences.t('mobile.voice') }
])

const activeChannelLabel = computed(() => {
  const activeChannel = shell.activeChannel

  if (!activeChannel) {
    return preferences.t('channel.none')
  }

  return activeChannel.type === 'GUILD_TEXT'
    ? `# ${activeChannel.name}`
    : `${preferences.t('channel.kind.voice')} ${activeChannel.name}`
})
const searchableChannels = computed(() => {
  const normalizedQuery = searchQuery.value.trim().toLowerCase()

  return shell.channelGroups
    .flatMap((group) =>
      group.channels.map((channel) => ({
        ...channel,
        groupName: group.name,
        unreadCount: shell.unreadCountForChannel(channel.id)
      }))
    )
    .filter((channel) => {
      if (!normalizedQuery) {
        return true
      }

      return `${channel.name} ${channel.groupName} ${channel.type}`.toLowerCase().includes(normalizedQuery)
    })
})
const messageSearchResults = computed(() => shell.messageSearchResults(searchQuery.value))
const voiceChannels = computed(() =>
  shell.channelGroups
    .flatMap((group) => group.channels)
    .filter((channel) => channel.type === 'GUILD_VOICE')
)
const unreadInboxChannels = computed(() =>
  shell.channelGroups
    .flatMap((group) =>
      group.channels.map((channel) => ({
        ...channel,
        groupName: group.name,
        unreadCount: shell.unreadCountForChannel(channel.id)
      }))
    )
    .filter((channel) => channel.unreadCount > 0)
)
const directMessageInboxItems = computed(() =>
  shell.socialDirectSummaries.filter((dm) => dm.unreadCount > 0 && dm.friend)
)
const statusText = computed(() => `${preferences.t('status.ready')} ${shell.gateway.lastSequence}`)

function updatePlatformSurface() {
  platformSurface.value = window.matchMedia('(max-width: 720px)').matches ? 'pwa-mobile' : 'web-desktop'
}

function updateLocale(event: Event) {
  preferences.setLocale((event.target as HTMLSelectElement).value as LocaleId)
}

function updateTheme(event: Event) {
  preferences.setTheme((event.target as HTMLSelectElement).value as ThemeId)
}

function setWorkbenchView(view: WorkbenchView) {
  activeWorkbenchView.value = view
  bottomPanelOpen.value = false
  if (view === 'inbox' && auth.accessToken) {
    void loadInboxNotifications(auth.accessToken)
  }
}

async function loadInboxNotifications(bearerToken: string) {
  const generation = shell.accountGeneration
  const items = await shell.loadBackendNotifications(bearerToken)
  if (items && generation === shell.accountGeneration) {
    inboxHasMore.value = items.length === 50
  }
}

async function loadMoreInboxNotifications() {
  const lastItem = shell.backendNotifications.at(-1)
  const bearerToken = auth.accessToken
  if (!inboxHasMore.value || inboxLoadingMore.value || !lastItem || !bearerToken) return
  const generation = shell.accountGeneration
  inboxLoadingMore.value = true
  try {
    const items = await shell.loadBackendNotificationPage(bearerToken, lastItem.id)
    if (!items || generation !== shell.accountGeneration || shell.backendNotifications.at(-1)?.id !== lastItem.id) return
    const existingIds = new Set(shell.backendNotifications.map((item) => item.id))
    const newItems = items.filter((item) => {
      if (existingIds.has(item.id)) return false
      existingIds.add(item.id)
      return true
    })
    shell.backendNotifications.push(...newItems)
    inboxHasMore.value = items.length === 50
  } finally {
    if (generation === shell.accountGeneration) inboxLoadingMore.value = false
  }
}

async function openBackendNotification(item: BackendNotification) {
  if (!auth.accessToken) {
    return
  }
  if (item.kind === 'MENTION') {
    openInboxChannel(item.channelId)
  } else if (item.kind === 'DM' && item.targetUserId) {
    activeWorkbenchView.value = 'inbox'
    await shell.openBackendDm(item.targetUserId, auth.accessToken)
  } else if (item.kind === 'SERVER' && item.guildId && item.eventId) {
    activeWorkbenchView.value = 'inbox'
    await shell.openBackendEvent(item.guildId, item.eventId, item.id, auth.accessToken)
  }
}

async function sendBackendDm() {
  if (!auth.accessToken || !shell.backendDmTargetUserId) {
    return
  }
  const generation = shell.accountGeneration
  if (await shell.sendBackendDm(shell.backendDmTargetUserId, backendDmDraft.value, auth.accessToken)
      && generation === shell.accountGeneration) {
    backendDmDraft.value = ''
  }
}

async function enableBrowserNotifications() {
  if (!('Notification' in window)) {
    browserNotificationPermission.value = 'unsupported'
    return
  }
  browserNotificationPermission.value = await Notification.requestPermission()
  browserNotificationsEnabled.value = browserNotificationPermission.value === 'granted'
  window.localStorage.setItem('discord-browser-notifications', String(browserNotificationsEnabled.value))
  void syncBrowserNotificationPolling()
}

async function pollBackendNotifications() {
  if (!mounted || notificationPollingPending || !auth.accessToken || document.visibilityState !== 'hidden' || !browserNotificationsEnabled.value
      || !('Notification' in window) || Notification.permission !== 'granted') {
    return
  }
  const token = auth.accessToken
  const generation = notificationPollingGeneration
  const accountGeneration = shell.accountGeneration
  notificationPollingPending = true
  try {
    const firstPage = await shell.loadBackendNotifications(token)
    if (!firstPage || generation !== notificationPollingGeneration || auth.accessToken !== token
        || document.visibilityState !== 'hidden') {
      return
    }
    const items = [...firstPage]
    let page = items
    // ponytail: at most 500 items per poll; use a server delta cursor if volume exceeds this window.
    let pages = 1
    while (pages++ < 10 && page.length === 50 && !page.some((item) => seenNotificationIds.has(item.id) || Date.parse(item.createdAt) < notificationWatermark)) {
      const older = await shell.loadBackendNotificationPage(token, page[page.length - 1]!.id)
      if (!older || generation !== notificationPollingGeneration || auth.accessToken !== token
          || document.visibilityState !== 'hidden') {
        return
      }
      items.push(...older)
      page = older
    }
    for (const item of items) {
      if (!seenNotificationIds.has(item.id) && !item.read && Date.parse(item.createdAt) >= notificationWatermark) {
        const notice = new Notification(`${item.kind} · ${item.summary}`, { body: '', tag: item.id })
        notice.onclick = () => {
          if (!mounted || accountGeneration !== shell.accountGeneration || !auth.accessToken) {
            notice.close()
            return
          }
          window.focus()
          void openBackendNotification(item)
          notice.close()
        }
      }
      seenNotificationIds.add(item.id)
    }
    notificationWatermark = Math.max(notificationWatermark, ...items.map((item) => Date.parse(item.createdAt)).filter(Number.isFinite))
    while (seenNotificationIds.size > 1_000) seenNotificationIds.delete(seenNotificationIds.values().next().value!)
  } finally {
    if (generation === notificationPollingGeneration) notificationPollingPending = false
  }
}

async function syncBrowserNotificationPolling() {
  const generation = ++notificationPollingGeneration
  notificationPollingPending = false
  if (notificationPollTimer !== null) {
    window.clearInterval(notificationPollTimer)
    notificationPollTimer = null
  }
  browserNotificationPermission.value = 'Notification' in window ? Notification.permission : 'unsupported'
  if (!mounted || !browserNotificationsEnabled.value || browserNotificationPermission.value !== 'granted'
      || !auth.accessToken || document.visibilityState !== 'hidden') {
    return
  }
  const token = auth.accessToken
  const baselineTime = Date.now()
  const items = await shell.loadBackendNotifications(token)
  if (!items || generation !== notificationPollingGeneration || document.visibilityState !== 'hidden' || auth.accessToken !== token) {
    return
  }
  seenNotificationIds.clear()
  items.forEach((item) => seenNotificationIds.add(item.id))
  notificationWatermark = items.length ? Math.max(...items.map((item) => Date.parse(item.createdAt)).filter(Number.isFinite), 0) : baselineTime
  notificationPollTimer = window.setInterval(() => { void pollBackendNotifications() }, 10_000)
}

watch(() => [auth.accessToken, auth.user?.id, shell.accountGeneration, activeWorkbenchView.value, shell.backendDmTargetUserId, shell.backendDmMessages.map((message) => `${message.id}:${message.sequence}`).join(',')], async () => {
  const generation = ++backendDmReadGeneration
  backendDmReadObserver?.disconnect()
  backendDmReadObserver = null
  if (!mounted || activeWorkbenchView.value !== 'inbox' || !auth.accessToken || !shell.backendDmTargetUserId || typeof IntersectionObserver === 'undefined') {
    return
  }
  const targetUserId = shell.backendDmTargetUserId
  const latest = shell.backendDmMessages.reduce<(typeof shell.backendDmMessages)[number] | null>(
    (current, message) => !current || message.sequence > current.sequence ? message : current, null
  )
  if (!latest || latest.sequence <= (shell.backendDmReadMarkers[shell.backendDmTargetUserId] ?? 0)) {
    return
  }
  await nextTick()
  if (!mounted || generation !== backendDmReadGeneration) return
  const element = Array.from(backendDmSection.value?.querySelectorAll<HTMLElement>('[data-dm-message-id]') ?? [])
    .find((candidate) => candidate.dataset.dmMessageId === latest.id)
  if (!element) {
    return
  }
  let pending = false
  const observer = new IntersectionObserver(async (entries) => {
    if (generation !== backendDmReadGeneration || document.visibilityState === 'hidden'
        || !entries.some((entry) => entry.isIntersecting) || pending || !auth.accessToken
        || shell.backendDmTargetUserId !== targetUserId) {
      return
    }
    pending = true
    const saved = await shell.markBackendDmRead(targetUserId, latest.sequence, auth.accessToken)
    pending = false
    if (saved) {
      observer.disconnect()
    }
  })
  backendDmReadObserver = observer
  observer.observe(element)
}, { flush: 'post' })

function openInboxChannel(channelId: string) {
  if (auth.accessToken) {
    void shell.selectBackendChannel(channelId, auth.accessToken)
  } else {
    shell.selectChannel(channelId)
  }
  activeWorkbenchView.value = 'explorer'
  activeMobilePane.value = 'chat'
}

function openInboxDirectMessage(directMessageId: string) {
  if (shell.selectDirectDm(directMessageId)) {
    activeWorkbenchView.value = 'explorer'
  }
}

definePageMeta({
  path: '/',
  alias: ['/app']
})

watch(() => [auth.user?.id, auth.accessToken, shell.accountGeneration] as const, ([userId, bearerToken]) => {
  inboxHasMore.value = false
  inboxLoadingMore.value = false
  if (activeWorkbenchView.value === 'inbox' && userId && bearerToken) {
    void loadInboxNotifications(bearerToken)
  }
})

onMounted(() => {
  void auth.restoreSession().then((restored) => {
    if (restored && auth.accessToken) {
      return shell.bootstrapCurrentUserWorkspace(auth.accessToken)
    }
    return null
  })
  preferences.hydrate()
  preferences.applyTheme()
  updatePlatformSurface()
  window.addEventListener('resize', updatePlatformSurface)
  browserNotificationsEnabled.value = window.localStorage.getItem('discord-browser-notifications') === 'true'
  browserNotificationPermission.value = 'Notification' in window ? Notification.permission : 'unsupported'
  document.addEventListener('visibilitychange', syncBrowserNotificationPolling)
  mobileShellReady.value = true

  if ('serviceWorker' in navigator) {
    void navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined)
  }
})

watch(() => preferences.themeId, () => preferences.applyTheme(), { immediate: true })
watch(() => [auth.accessToken, auth.user?.id, shell.accountGeneration], () => { void syncBrowserNotificationPolling() })
watch(() => [auth.accessToken, auth.user?.id] as const, ([token, userId], [oldToken, oldUserId]) => {
  if (userId !== oldUserId || (token !== oldToken && (!userId || !auth.isApplyingSession)) || !token) {
    shell.resetAccountState()
    backendDmDraft.value = ''
    seenNotificationIds.clear()
    ++notificationPollingGeneration
    ++backendDmReadGeneration
    backendDmReadObserver?.disconnect()
  }
}, { flush: 'sync' })

onBeforeUnmount(() => {
  mounted = false
  ++notificationPollingGeneration
  ++backendDmReadGeneration
  window.removeEventListener('resize', updatePlatformSurface)
  document.removeEventListener('visibilitychange', syncBrowserNotificationPolling)
  if (notificationPollTimer !== null) {
    window.clearInterval(notificationPollTimer)
  }
  backendDmReadObserver?.disconnect()
})
</script>

<template>
  <main
    class="vscode-shell app-shell"
    data-testid="vscode-shell"
    aria-label="Discord clone workspace"
    :data-platform-surface="platformSurface"
    :data-locale="preferences.locale"
    :data-theme-id="preferences.themeId"
  >
    <a class="skip-link" data-testid="skip-to-workspace" href="#workspace-content">Skip to workspace</a>
    <header class="global-title-bar workbench-titlebar" data-testid="global-title-bar" aria-label="Global navigation">
      <span class="global-title-bar-title">{{ preferences.t('app.title') }}</span>
      <strong>{{ activeChannelLabel }}</strong>
      <nav class="workbench-prefs" aria-label="Workspace preferences">
        <label>
          {{ preferences.t('status.locale') }}
          <select data-testid="locale-select" :value="preferences.locale" @change="updateLocale">
            <option v-for="locale in preferences.localeOptions" :key="locale.id" :value="locale.id">
              {{ locale.label }}
            </option>
          </select>
        </label>
        <label>
          {{ preferences.t('status.theme') }}
          <select data-testid="theme-select" :value="preferences.themeId" @change="updateTheme">
            <option v-for="theme in preferences.themeOptions" :key="theme.id" :value="theme.id">
              {{ theme.label }}
            </option>
          </select>
        </label>
      </nav>
    </header>
    <aside class="account-banner workbench-banner" data-testid="account-banner" aria-label="Account registration notice">
      <span>{{ preferences.t('banner') }}</span>
    </aside>
    <aside class="activity-bar" data-testid="activity-bar" aria-label="Activity bar">
      <ServerRail />
      <button
        type="button"
        data-testid="activity-explorer"
        :aria-current="activeWorkbenchView === 'explorer' ? 'page' : undefined"
        :title="preferences.t('activity.explorer')"
        @click="setWorkbenchView('explorer')"
      >
        <strong>{{ preferences.t('activity.explorer') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-search"
        :aria-current="activeWorkbenchView === 'search' ? 'page' : undefined"
        :title="preferences.t('activity.search')"
        @click="setWorkbenchView('search')"
      >
        <strong>{{ preferences.t('activity.search') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-inbox"
        :aria-current="activeWorkbenchView === 'inbox' ? 'page' : undefined"
        :title="preferences.t('activity.inbox')"
        @click="setWorkbenchView('inbox')"
      >
        <strong>{{ preferences.t('activity.inbox') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-calls"
        :aria-current="activeWorkbenchView === 'calls' ? 'page' : undefined"
        :title="preferences.t('activity.calls')"
        @click="setWorkbenchView('calls')"
      >
        <strong>{{ preferences.t('activity.calls') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-settings"
        :aria-current="activeWorkbenchView === 'settings' ? 'page' : undefined"
        :title="preferences.t('activity.settings')"
        @click="setWorkbenchView('settings')"
      >
        <strong>{{ preferences.t('activity.settings') }}</strong>
      </button>
    </aside>
    <header class="mobile-shell-bar" data-testid="mobile-shell-bar">
      <span>{{ preferences.t('app.title') }}</span>
      <strong>{{ activeChannelLabel }}</strong>
    </header>
    <section
      class="workbench workspace"
      id="workspace-content"
      :class="[`mobile-pane-${activeMobilePane}`, { 'bottom-panel-open': bottomPanelOpen }]"
      data-testid="workspace"
      tabindex="-1"
      :data-mobile-active-pane="activeMobilePane"
      :data-active-workbench-view="activeWorkbenchView"
      :data-bottom-panel-open="bottomPanelOpen"
    >
      <p v-if="shell.apiError" class="shell-api-error" data-testid="shell-api-error" role="alert">
        {{ shell.apiError }}
      </p>
      <aside
        v-if="activeWorkbenchView === 'explorer'"
        class="workspace-explorer"
        data-testid="workspace-explorer"
        aria-label="Workspace explorer"
      >
        <header>
          <p>{{ preferences.t('activity.explorer') }}</p>
          <h2>{{ shell.guild.name }}</h2>
        </header>
        <p class="explorer-section-label">{{ preferences.t('channels.text') }}</p>
        <ChannelSidebar @click.capture="activeMobilePane = 'chat'" />
        <p class="explorer-section-label">{{ preferences.t('channels.voice') }}</p>
        <UserPanel />
      </aside>
      <section
        v-if="activeWorkbenchView === 'explorer'"
        class="editor-workbench"
        data-testid="editor-chat"
        aria-label="Chat editor"
      >
        <header class="editor-titlebar" data-testid="editor-titlebar">
          <span>{{ activeChannelLabel }}</span>
          <small>{{ preferences.t('editor.hint') }}</small>
        </header>
        <ChatViewport />
        <p class="editor-empty-hint" data-testid="editor-empty-hint">{{ preferences.t('editor.hint') }}</p>
      </section>
      <aside v-if="activeWorkbenchView === 'explorer'" class="side-panel" data-testid="side-panel">
        <MemberSidebar />
      </aside>
      <section
        v-if="activeWorkbenchView === 'explorer'"
        id="bottom-panel"
        class="bottom-panel"
        data-testid="bottom-panel"
        aria-label="Workbench panel"
      >
        <header class="bottom-panel-titlebar" data-testid="bottom-panel-titlebar">
          <strong>Gateway & voice</strong>
          <button
            type="button"
            data-testid="bottom-panel-close"
            aria-label="Close workbench panel"
            @click="bottomPanelOpen = false"
          >
            Close
          </button>
        </header>
        <GatewayStatusPanel />
        <VoicePanel />
      </section>
      <section
        v-if="activeWorkbenchView === 'search'"
        class="workbench-secondary-view workbench-search-view"
        data-testid="workbench-search-view"
        aria-label="Search workspace"
      >
        <header>
          <p>{{ preferences.t('activity.search') }}</p>
          <h2>{{ preferences.t('view.search.title') }}</h2>
          <span>{{ preferences.t('view.search.description') }}</span>
        </header>
        <label class="workbench-field">
          {{ preferences.t('view.search.input') }}
          <input
            v-model="searchQuery"
            data-testid="workbench-search-input"
            type="search"
            :placeholder="preferences.t('view.search.placeholder')"
          >
        </label>
        <section class="workbench-list" data-testid="workbench-search-results">
          <article
            v-for="message in messageSearchResults"
            :key="`message-${message.id}`"
            class="workbench-list-row"
            :data-testid="`workbench-search-message-${message.id}`"
          >
            <button type="button" @click="openInboxChannel(message.channelId)">
              # {{ message.channelName }} - {{ message.body }}
            </button>
            <small>{{ message.author }}</small>
            <button
              type="button"
              :data-testid="`workbench-report-message-${message.id}`"
              @click="shell.reportMessage(message.id, auth.accessToken)"
            >
              {{ preferences.t('view.search.report') }}
            </button>
          </article>
          <article
            v-for="channel in searchableChannels"
            :key="channel.id"
            class="workbench-list-row"
            :data-testid="`workbench-search-result-${channel.id}`"
          >
            <span>{{ channel.type === 'GUILD_TEXT' ? '#' : preferences.t('channel.kind.voice') }} {{ channel.name }}</span>
            <small>{{ channel.groupName }}</small>
            <strong v-if="channel.unreadCount > 0">{{ channel.unreadCount }}</strong>
          </article>
        </section>
        <section class="workbench-list" data-testid="workbench-report-queue">
          <article
            v-for="report in shell.openMessageReports"
            :key="report.id"
            class="workbench-list-row"
            :data-testid="`workbench-report-${report.id}`"
          >
            <span>{{ preferences.t('view.search.reportReason.moderatorReview') }}</span>
            <small>{{ report.messageId }}</small>
            <button
              type="button"
              :data-testid="`workbench-resolve-report-${report.id}`"
              @click="shell.resolveMessageReport(report.id, 'RESOLVED', auth.accessToken)"
            >
              {{ preferences.t('view.search.resolveReport') }}
            </button>
          </article>
        </section>
      </section>
      <section
        v-if="activeWorkbenchView === 'inbox'"
        class="workbench-secondary-view workbench-inbox-view"
        data-testid="workbench-inbox-view"
        :aria-label="preferences.t('view.inbox.title')"
      >
        <header>
          <p>{{ preferences.t('activity.inbox') }}</p>
          <h2>{{ preferences.t('view.inbox.title') }}</h2>
          <span>{{ preferences.t('view.inbox.description') }}</span>
        </header>
        <section class="workbench-list" data-testid="workbench-inbox-mentions">
          <article
            v-for="item in shell.mentionInboxItems"
            :key="`mention-${item.id}`"
            class="workbench-list-row"
            :data-testid="`workbench-inbox-mention-${item.id}`"
          >
            <button type="button" @click="openInboxChannel(item.channelId)">
              # {{ item.channelName }}
            </button>
            <small>{{ item.author }}</small>
            <strong>@</strong>
          </article>
        </section>
        <section v-if="auth.accessToken" class="workbench-list" data-testid="workbench-inbox-server-items">
          <article
            v-for="item in shell.backendNotifications.filter((candidate) => candidate.kind !== 'MENTION')"
            :key="item.id"
            class="workbench-list-row"
            :data-testid="`workbench-inbox-${item.kind.toLowerCase()}-${item.id}`"
          >
            <button type="button" @click="openBackendNotification(item)">{{ item.summary }}</button>
            <small>{{ item.kind }}</small>
            <strong v-if="!item.read">●</strong>
          </article>
        </section>
        <button
          v-if="auth.accessToken && inboxHasMore"
          type="button"
          data-testid="workbench-inbox-load-more"
          :disabled="inboxLoadingMore"
          @click="loadMoreInboxNotifications"
        >
          {{ preferences.t('view.inbox.loadMore') }}
        </button>
        <section v-if="auth.accessToken && shell.backendDmTargetUserId" ref="backendDmSection" data-testid="workbench-inbox-dm-conversation">
          <h3>{{ preferences.t('view.inbox.directMessage') }}</h3>
          <p v-for="message in shell.backendDmMessages" :key="message.id" :data-dm-message-id="message.id">{{ message.content }}</p>
          <form @submit.prevent="sendBackendDm">
            <input v-model="backendDmDraft" data-testid="workbench-inbox-dm-input" aria-label="Direct message">
            <button type="submit">{{ preferences.t('composer.send') }}</button>
          </form>
        </section>
        <section v-if="auth.accessToken && shell.backendOpenEvent" data-testid="workbench-inbox-event-detail">
          <h3>{{ shell.backendOpenEvent.title }}</h3>
          <p>{{ shell.backendOpenEvent.startsAt }}</p>
        </section>
        <section class="workbench-list" data-testid="workbench-inbox-unreads">
          <article
            v-for="channel in unreadInboxChannels"
            :key="`unread-${channel.id}`"
            class="workbench-list-row"
            :data-testid="`workbench-inbox-unread-${channel.id}`"
          >
            <button type="button" @click="openInboxChannel(channel.id)">
              # {{ channel.name }}
            </button>
            <small>{{ channel.groupName }}</small>
            <strong>{{ channel.unreadCount }}</strong>
          </article>
          <template v-if="!auth.accessToken">
            <article
              v-for="dm in directMessageInboxItems"
              :key="`dm-${dm.id}`"
              class="workbench-list-row"
              :data-testid="`workbench-inbox-dm-${dm.id}`"
            >
              <button type="button" @click="openInboxDirectMessage(dm.id)">
                {{ dm.friend?.name }}
              </button>
              <small>{{ preferences.t('view.inbox.directMessage') }}</small>
              <strong>{{ dm.unreadCount }}</strong>
            </article>
          </template>
        </section>
      </section>
      <section
        v-if="activeWorkbenchView === 'calls'"
        class="workbench-secondary-view workbench-calls-view"
        data-testid="workbench-calls-view"
        aria-label="Calls workspace"
      >
        <header>
          <p>{{ preferences.t('activity.calls') }}</p>
          <h2>{{ preferences.t('view.calls.title') }}</h2>
          <span>{{ preferences.t('view.calls.description') }}</span>
        </header>
        <section class="workbench-list">
          <article
            v-for="channel in voiceChannels"
            :key="channel.id"
            class="workbench-list-row"
            :data-testid="`workbench-call-channel-${channel.id}`"
          >
            <span>{{ preferences.t('channel.kind.voice') }} {{ channel.name }}</span>
            <small>{{ shell.voice.activeChannelId === channel.id ? preferences.t('view.calls.active') : preferences.t('view.calls.ready') }}</small>
          </article>
        </section>
        <VoicePanel />
      </section>
      <section
        v-if="activeWorkbenchView === 'settings'"
        class="workbench-secondary-view workbench-settings-view"
        data-testid="workbench-settings-view"
        aria-label="Workspace settings"
      >
        <header>
          <p>{{ preferences.t('activity.settings') }}</p>
          <h2>{{ preferences.t('view.settings.title') }}</h2>
          <span>{{ preferences.t('view.settings.description') }}</span>
        </header>
        <label class="workbench-field">
          {{ preferences.t('status.locale') }}
          <select data-testid="settings-locale-select" :value="preferences.locale" @change="updateLocale">
            <option v-for="locale in preferences.localeOptions" :key="locale.id" :value="locale.id">
              {{ locale.label }}
            </option>
          </select>
        </label>
        <label class="workbench-field">
          {{ preferences.t('status.theme') }}
          <select data-testid="settings-theme-select" :value="preferences.themeId" @change="updateTheme">
            <option v-for="theme in preferences.themeOptions" :key="theme.id" :value="theme.id">
              {{ theme.label }}
            </option>
          </select>
        </label>
        <button
          type="button"
          data-testid="settings-browser-notifications-enable"
          :disabled="browserNotificationPermission === 'unsupported' || browserNotificationPermission === 'denied'"
          @click="enableBrowserNotifications"
        >
          {{ preferences.t('view.settings.browserNotifications') }}
        </button>
        <p data-testid="settings-browser-notifications-status">{{ preferences.t(`view.settings.browserNotifications.${browserNotificationPermission}`) }}</p>
        <dl class="workbench-settings-summary">
          <div>
            <dt>{{ preferences.t('view.settings.guild') }}</dt>
            <dd>{{ shell.guild.name }}</dd>
          </div>
          <div>
            <dt>{{ preferences.t('view.settings.channel') }}</dt>
            <dd>{{ activeChannelLabel }}</dd>
          </div>
        </dl>
        <UserPanel />
      </section>
      <footer class="status-bar" data-testid="status-bar" aria-label="Status bar">
        <button
          type="button"
          class="status-panel-toggle"
          data-testid="bottom-panel-toggle"
          :aria-expanded="bottomPanelOpen"
          aria-controls="bottom-panel"
          @click="bottomPanelOpen = !bottomPanelOpen"
        >
          Panel
        </button>
        <span>{{ statusText }}</span>
        <span>{{ activeChannelLabel }}</span>
        <span>{{ preferences.t('status.locale') }}: {{ preferences.locale }}</span>
        <span>{{ preferences.t('status.theme') }}: {{ preferences.theme.label }}</span>
      </footer>
    </section>
    <nav class="mobile-shell-nav" data-testid="mobile-shell-nav" aria-label="Mobile shell navigation">
      <button
        v-for="pane in mobilePanes"
        :key="pane.id"
        type="button"
        role="tab"
        :data-testid="`mobile-nav-${pane.id}`"
        :aria-selected="activeMobilePane === pane.id"
        :disabled="!mobileShellReady"
        @click="activeMobilePane = pane.id"
      >
        {{ pane.label }}
      </button>
    </nav>
  </main>
</template>
