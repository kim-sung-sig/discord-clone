import { defineStore } from 'pinia'
import enUs from '../locales/en-US.json'
import koKr from '../locales/ko-KR.json'
import cosmosTheme from '../themes/cosmos.json'
import vscodeDarkTheme from '../themes/vscode-dark.json'
import vscodeLightTheme from '../themes/vscode-light.json'

type LocaleId = 'en-US' | 'ko-KR'
type ThemeId = 'vscode-dark' | 'vscode-light' | 'cosmos'
type LocaleMessages = typeof enUs
type MessageKey = keyof LocaleMessages

type ThemeDefinition = {
  id: ThemeId
  label: string
  tokens: {
    workbenchBg: string
    activityBg: string
    sideBarBg: string
    editorBg: string
    panelBg: string
    statusBg: string
    titleBg: string
    foreground: string
    muted: string
    accent: string
    border: string
  }
}

const localeMessages: Record<LocaleId, LocaleMessages> = {
  'en-US': enUs,
  'ko-KR': koKr
}

const themeDefinitions: Record<ThemeId, ThemeDefinition> = {
  'vscode-dark': vscodeDarkTheme as ThemeDefinition,
  'vscode-light': vscodeLightTheme as ThemeDefinition,
  cosmos: cosmosTheme as ThemeDefinition
}

const isClient = () => typeof document !== 'undefined'

export const usePreferencesStore = defineStore('preferences', {
  state: () => ({
    locale: 'en-US' as LocaleId,
    themeId: 'vscode-dark' as ThemeId
  }),
  getters: {
    localeOptions: () => [
      { id: 'en-US' as LocaleId, label: 'English' },
      { id: 'ko-KR' as LocaleId, label: '한국어' }
    ],
    themeOptions: () => Object.values(themeDefinitions).map(({ id, label }) => ({ id, label })),
    theme(state): ThemeDefinition {
      return themeDefinitions[state.themeId]
    }
  },
  actions: {
    t(key: MessageKey): string {
      return localeMessages[this.locale][key] ?? localeMessages['en-US'][key]
    },
    hydrate() {
      if (typeof window === 'undefined') {
        return
      }
      const storedLocale = window.localStorage.getItem('discord-locale') as LocaleId | null
      const storedTheme = window.localStorage.getItem('discord-theme') as ThemeId | null
      if (storedLocale && storedLocale in localeMessages) {
        this.locale = storedLocale
      }
      if (storedTheme && storedTheme in themeDefinitions) {
        this.themeId = storedTheme
      }
    },
    persist() {
      if (typeof window === 'undefined') {
        return
      }
      window.localStorage.setItem('discord-locale', this.locale)
      window.localStorage.setItem('discord-theme', this.themeId)
    },
    applyTheme() {
      if (!isClient()) {
        return
      }
      const { tokens } = this.theme
      document.documentElement.dataset.theme = this.themeId
      const targets = [
        document.documentElement,
        ...Array.from(document.querySelectorAll<HTMLElement>('[data-testid="vscode-shell"]'))
      ]
      for (const target of targets) {
        target.style.setProperty('--workbench-bg', tokens.workbenchBg)
        target.style.setProperty('--activity-bg', tokens.activityBg)
        target.style.setProperty('--sidebar-bg', tokens.sideBarBg)
        target.style.setProperty('--editor-bg', tokens.editorBg)
        target.style.setProperty('--workbench-panel-bg', tokens.panelBg)
        target.style.setProperty('--status-bg', tokens.statusBg)
        target.style.setProperty('--title-bg', tokens.titleBg)
        target.style.setProperty('--workbench-fg', tokens.foreground)
        target.style.setProperty('--workbench-muted', tokens.muted)
        target.style.setProperty('--workbench-accent', tokens.accent)
        target.style.setProperty('--workbench-border', tokens.border)
      }
    },
    setLocale(locale: LocaleId) {
      this.locale = locale
      this.persist()
    },
    setTheme(themeId: ThemeId) {
      this.themeId = themeId
      this.applyTheme()
      this.persist()
    }
  }
})

export type { LocaleId, MessageKey, ThemeId }
