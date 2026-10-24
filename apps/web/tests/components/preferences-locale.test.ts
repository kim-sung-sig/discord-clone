import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import enUs from '../../locales/en-US.json'
import koKr from '../../locales/ko-KR.json'
import { detectLocale, usePreferencesStore } from '../../stores/preferences'

const stubBrowserLanguages = (languages: string[], language = languages[0] ?? '') => {
  vi.stubGlobal('navigator', { languages, language })
}

describe('detectLocale', () => {
  it.each([
    [['ko-KR'], 'ko-KR'],
    [['ko'], 'ko-KR'],
    [['KO-kr'], 'ko-KR'],
    [['en-US'], 'en-US'],
    [['en-GB'], 'en-US'],
    [['ja-JP'], 'en-US'],
    [['fr', 'de'], 'en-US'],
    [[], 'en-US']
  ] as const)('maps %j to %s', (languages, expected) => {
    expect(detectLocale(languages)).toBe(expected)
  })

  it('uses the most preferred supported language in the list', () => {
    expect(detectLocale(['ko-KR', 'en-US'])).toBe('ko-KR')
    expect(detectLocale(['en-US', 'ko-KR'])).toBe('en-US')
    expect(detectLocale(['ja-JP', 'ko-KR'])).toBe('ko-KR')
  })

  it('falls back to en-US when no language list is available', () => {
    expect(detectLocale(undefined)).toBe('en-US')
    expect(detectLocale(null)).toBe('en-US')
  })
})

describe('preferences store locale hydration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    document.documentElement.removeAttribute('lang')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('picks ko-KR from a Korean browser when nothing is stored', () => {
    stubBrowserLanguages(['ko-KR', 'en-US'])
    const preferences = usePreferencesStore()

    preferences.hydrate()

    expect(preferences.locale).toBe('ko-KR')
    expect(document.documentElement.lang).toBe('ko-KR')
    expect(window.localStorage.getItem('discord-locale')).toBeNull()
  })

  it('picks en-US for non-Korean browsers when nothing is stored', () => {
    stubBrowserLanguages(['ja-JP'])
    const preferences = usePreferencesStore()

    preferences.hydrate()

    expect(preferences.locale).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
  })

  it('falls back to navigator.language when navigator.languages is empty', () => {
    stubBrowserLanguages([], 'ko')
    const preferences = usePreferencesStore()

    preferences.hydrate()

    expect(preferences.locale).toBe('ko-KR')
  })

  it('lets a stored preference win over the browser language', () => {
    stubBrowserLanguages(['ko-KR'])
    window.localStorage.setItem('discord-locale', 'en-US')
    const preferences = usePreferencesStore()

    preferences.hydrate()

    expect(preferences.locale).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
  })

  it('ignores an unsupported stored value and detects from the browser', () => {
    stubBrowserLanguages(['ko-KR'])
    window.localStorage.setItem('discord-locale', 'fr-FR')
    const preferences = usePreferencesStore()

    preferences.hydrate()

    expect(preferences.locale).toBe('ko-KR')
  })

  it('keeps <html lang> in sync and persists when the user picks a locale', () => {
    stubBrowserLanguages(['en-US'])
    const preferences = usePreferencesStore()
    preferences.hydrate()

    preferences.setLocale('ko-KR')

    expect(document.documentElement.lang).toBe('ko-KR')
    expect(window.localStorage.getItem('discord-locale')).toBe('ko-KR')
    expect(preferences.t('login.submit')).toBe(koKr['login.submit'])
  })
})

describe('locale catalogs', () => {
  it('keep identical key sets', () => {
    expect(Object.keys(koKr).sort()).toEqual(Object.keys(enUs).sort())
  })

  it('have no empty values', () => {
    for (const catalog of [enUs, koKr]) {
      for (const [key, value] of Object.entries(catalog)) {
        expect(value.trim(), key).not.toBe('')
      }
    }
  })

  it('do not leak developer wording to users', () => {
    const forbidden = /Spring Boot|httpOnly|access token|skeleton|SFU|bootstrap|VS Code|스켈레톤|부트스트랩/i
    for (const catalog of [enUs, koKr]) {
      for (const [key, value] of Object.entries(catalog)) {
        expect(value, key).not.toMatch(forbidden)
      }
    }
  })
})
