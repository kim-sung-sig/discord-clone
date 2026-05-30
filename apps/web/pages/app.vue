<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ServerRail from '../components/shell/ServerRail.vue'
import ChannelSidebar from '../components/shell/ChannelSidebar.vue'
import ChatViewport from '../components/shell/ChatViewport.vue'
import ForumPanel from '../components/shell/ForumPanel.vue'
import ModerationPanel from '../components/shell/ModerationPanel.vue'
import VoicePanel from '../components/shell/VoicePanel.vue'
import ExperiencePanel from '../components/shell/ExperiencePanel.vue'
import InviteModal from '../components/invite/InviteModal.vue'
import DmSidebar from '../components/social/DmSidebar.vue'
import MemberSidebar from '../components/shell/MemberSidebar.vue'
import RolePermissionPanel from '../components/shell/RolePermissionPanel.vue'
import GatewayStatusPanel from '../components/shell/GatewayStatusPanel.vue'
import UserPanel from '../components/shell/UserPanel.vue'
import { useAuthStore } from '../stores/auth'
import { usePreferencesStore, type LocaleId, type ThemeId } from '../stores/preferences'
import { useShellStore } from '../stores/shell'

const auth = useAuthStore()
const shell = useShellStore()
const preferences = usePreferencesStore()

type MobilePane = 'channels' | 'chat' | 'members' | 'voice'
type WorkbenchView = 'explorer' | 'search' | 'calls' | 'settings'

const activeMobilePane = ref<MobilePane>('chat')
const activeWorkbenchView = ref<WorkbenchView>('explorer')
const bottomPanelOpen = ref(false)
const searchQuery = ref('')
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
const voiceChannels = computed(() =>
  shell.channelGroups
    .flatMap((group) => group.channels)
    .filter((channel) => channel.type === 'GUILD_VOICE')
)
const statusText = computed(() => `${preferences.t('status.ready')} ${shell.gateway.lastSequence}`)
const shellThemeStyle = computed(() => ({
  '--workbench-bg': preferences.theme.tokens.workbenchBg,
  '--activity-bg': preferences.theme.tokens.activityBg,
  '--sidebar-bg': preferences.theme.tokens.sideBarBg,
  '--editor-bg': preferences.theme.tokens.editorBg,
  '--workbench-panel-bg': preferences.theme.tokens.panelBg,
  '--status-bg': preferences.theme.tokens.statusBg,
  '--title-bg': preferences.theme.tokens.titleBg,
  '--workbench-fg': preferences.theme.tokens.foreground,
  '--workbench-muted': preferences.theme.tokens.muted,
  '--workbench-accent': preferences.theme.tokens.accent,
  '--workbench-border': preferences.theme.tokens.border
}))

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
}

async function runRealBackendSmoke() {
  if (!auth.accessToken) {
    shell.apiError = 'Sign in before running the real backend smoke.'
    return
  }
  const stamp = Date.now()
  const guild = await shell.createBackendGuild(`Web Real Guild ${stamp}`, auth.accessToken)
  if (!guild) {
    return
  }
  const text = await shell.createBackendChannel(guild.id, `real-text-${stamp}`, 'GUILD_TEXT', auth.accessToken)
  if (!text) {
    return
  }
  const voice = await shell.createBackendChannel(guild.id, `real-voice-${stamp}`, 'GUILD_VOICE', auth.accessToken)
  if (!voice) {
    return
  }
  await shell.sendBackendMessage(text.id, 'real backend hello', auth.accessToken)
  await shell.joinBackendVoice(voice.id, auth.accessToken)
  await shell.startBackendStage(voice.id, 'Web Real Stage', auth.accessToken)
}

definePageMeta({
  path: '/',
  alias: ['/app']
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
  mobileShellReady.value = true

  if ('serviceWorker' in navigator) {
    void navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined)
  }
})

watch(() => preferences.themeId, () => preferences.applyTheme(), { immediate: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', updatePlatformSurface)
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
    :style="shellThemeStyle"
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
      <button type="button">{{ preferences.t('banner.register') }}</button>
    </aside>
    <aside class="activity-bar" data-testid="activity-bar" aria-label="Activity bar">
      <ServerRail />
      <button
        type="button"
        data-testid="activity-explorer"
        :aria-current="activeWorkbenchView === 'explorer' ? 'page' : undefined"
        title="Explorer"
        @click="setWorkbenchView('explorer')"
      >
        <span>Files</span>
        <strong>{{ preferences.t('activity.explorer') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-search"
        :aria-current="activeWorkbenchView === 'search' ? 'page' : undefined"
        title="Search"
        @click="setWorkbenchView('search')"
      >
        <span>Find</span>
        <strong>{{ preferences.t('activity.search') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-calls"
        :aria-current="activeWorkbenchView === 'calls' ? 'page' : undefined"
        title="Calls"
        @click="setWorkbenchView('calls')"
      >
        <span>Call</span>
        <strong>{{ preferences.t('activity.calls') }}</strong>
      </button>
      <button
        type="button"
        data-testid="activity-settings"
        :aria-current="activeWorkbenchView === 'settings' ? 'page' : undefined"
        title="Settings"
        @click="setWorkbenchView('settings')"
      >
        <span>Gear</span>
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
      <section v-if="auth.accessToken" class="real-api-panel" data-testid="real-api-panel">
        <p>Real backend flow</p>
        <button
          type="button"
          data-testid="real-backend-smoke"
          :disabled="shell.apiBusy"
          @click="runRealBackendSmoke"
        >
          {{ shell.apiBusy ? 'Running...' : 'Run backend smoke' }}
        </button>
      </section>
      <section class="legacy-shell-contracts" data-testid="legacy-shell-contracts" aria-hidden="true">
        <DmSidebar />
        <ForumPanel />
        <ModerationPanel />
        <ExperiencePanel />
        <RolePermissionPanel />
        <InviteModal />
        <UserPanel />
      </section>
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
