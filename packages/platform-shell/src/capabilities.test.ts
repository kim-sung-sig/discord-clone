import { describe, expect, it } from 'vitest'

import { nativeOnlyCapabilities, platformCapabilities, requiredCapabilitiesForSurface } from './capabilities'

describe('platform capabilities', () => {
  it('does not require native-only capabilities for the PWA MVP', () => {
    const pwaRequiredCapabilities = requiredCapabilitiesForSurface('pwa-mobile').map((capability) => capability.name)

    expect(pwaRequiredCapabilities).not.toContain('push')
    expect(pwaRequiredCapabilities).not.toContain('background-session')
    expect(pwaRequiredCapabilities).not.toContain('native-file-picker')
    expect(nativeOnlyCapabilities.every((capability) => capability.requiredForMvp === false)).toBe(true)
  })

  it('does not make platform-specific capabilities globally required', () => {
    const globallyRequired = platformCapabilities.filter((capability) => capability.requiredForMvp)

    expect(globallyRequired.every((capability) => capability.surface !== 'native-mobile')).toBe(true)
  })
})
