import { mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import { surfaceScreenContract } from '@discord-clone/ui-contracts/screens'
import { requiredCapabilitiesForSurface } from '@discord-clone/platform-shell/capabilities'
import AppPage from '../../pages/app.vue'

describe('web platform shell contract usage', () => {
  it('renders controls required by the PWA mobile screen contract', async () => {
    const pwaContract = surfaceScreenContract('pwa-mobile')
    const requiredPwaCapabilities = requiredCapabilitiesForSurface('pwa-mobile')

    expect(pwaContract.navigation.mode).toBe('single-pane-tabs')
    expect(pwaContract.navigation.secondaryAccess).toEqual(
      expect.arrayContaining(['channel-drawer', 'member-sheet'])
    )
    expect(requiredPwaCapabilities).toEqual([
      expect.objectContaining({ name: 'offline-shell', requiredForMvp: true })
    ])

    const wrapper = await mountSuspended(AppPage)

    expect(wrapper.get('[data-testid="mobile-nav-channels"]').text()).toContain('Channels')
    expect(wrapper.get('[data-testid="mobile-nav-chat"]').text()).toContain('Chat')
    expect(wrapper.get('[data-testid="mobile-nav-members"]').text()).toContain('Members')
    expect(wrapper.get('[data-testid="mobile-nav-voice"]').text()).toContain('Voice')
  })
})
