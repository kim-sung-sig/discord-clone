import { describe, expect, it } from 'vitest'

import { permissionContractMap, permissionContracts } from './permissions'
import { screenContracts, surfaceScreenContract } from './screens'

describe('screen contracts', () => {
  it('defines exactly one screen contract for each supported surface', () => {
    const surfaces = screenContracts.map((contract) => contract.surface)

    expect(surfaces).toEqual(['web-desktop', 'pwa-mobile', 'desktop-app', 'native-mobile'])
    expect(new Set(surfaces).size).toBe(surfaces.length)
  })

  it('keeps mobile surfaces on single-pane navigation while desktop surfaces stay multi-pane', () => {
    expect(surfaceScreenContract('pwa-mobile').navigation.mode).toBe('single-pane-tabs')
    expect(surfaceScreenContract('native-mobile').navigation.mode).toBe('single-pane-stack')
    expect(surfaceScreenContract('web-desktop').navigation.mode).toBe('multi-pane')
    expect(surfaceScreenContract('desktop-app').navigation.mode).toBe('multi-pane')
  })

  it('requires auth and permission gates for desktop deep links and restored channels', () => {
    const desktop = surfaceScreenContract('desktop-app')

    expect(desktop.navigation.entry).toBe('deep-link-or-last-channel')
    expect(desktop.navigation.requiresAuth).toBe(true)
    expect(desktop.navigation.requiresPermissionCheck).toBe(true)
    expect(desktop.navigation.fallbackOnDenied).toBe('guild-list')
  })

  it('keeps permission contract entries unique and exhaustively mapped', () => {
    const actions = permissionContracts.map((contract) => contract.action)

    expect(new Set(actions).size).toBe(actions.length)
    expect(actions).toEqual(Object.keys(permissionContractMap))
  })
})
