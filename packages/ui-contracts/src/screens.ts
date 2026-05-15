import type { NavigationContract } from './navigation'

export type PlatformSurface = 'web-desktop' | 'pwa-mobile' | 'desktop-app' | 'native-mobile'

export type ShellPane = 'server-rail' | 'channel-list' | 'chat' | 'member-list' | 'voice' | 'me'

export interface ScreenContract {
  readonly surface: PlatformSurface
  readonly primaryPane: ShellPane
  readonly visiblePanes: readonly ShellPane[]
  readonly navigation: NavigationContract
}

export const screenContracts: readonly ScreenContract[] = [
  {
    surface: 'web-desktop',
    primaryPane: 'chat',
    visiblePanes: ['server-rail', 'channel-list', 'chat', 'member-list', 'voice', 'me'],
    navigation: {
      mode: 'multi-pane',
      entry: 'guild-channel',
      secondaryAccess: ['member-list'],
      requiresAuth: true,
      requiresPermissionCheck: true,
      fallbackOnDenied: 'guild-list'
    }
  },
  {
    surface: 'pwa-mobile',
    primaryPane: 'chat',
    visiblePanes: ['chat', 'voice', 'me'],
    navigation: {
      mode: 'single-pane-tabs',
      entry: 'guild-channel',
      secondaryAccess: ['server-drawer', 'channel-drawer', 'member-sheet'],
      requiresAuth: true,
      requiresPermissionCheck: true,
      fallbackOnDenied: 'guild-list'
    }
  },
  {
    surface: 'desktop-app',
    primaryPane: 'chat',
    visiblePanes: ['server-rail', 'channel-list', 'chat', 'member-list', 'voice', 'me'],
    navigation: {
      mode: 'multi-pane',
      entry: 'deep-link-or-last-channel',
      secondaryAccess: ['member-list'],
      requiresAuth: true,
      requiresPermissionCheck: true,
      fallbackOnDenied: 'guild-list'
    }
  },
  {
    surface: 'native-mobile',
    primaryPane: 'chat',
    visiblePanes: ['chat', 'voice', 'me'],
    navigation: {
      mode: 'single-pane-stack',
      entry: 'auth-or-last-channel',
      secondaryAccess: ['server-drawer', 'channel-drawer', 'member-sheet'],
      requiresAuth: true,
      requiresPermissionCheck: true,
      fallbackOnDenied: 'login'
    }
  }
] as const

export function surfaceScreenContract(surface: PlatformSurface): ScreenContract {
  const contract = screenContracts.find((candidate) => candidate.surface === surface)

  if (!contract) {
    throw new Error(`Missing screen contract for surface: ${surface}`)
  }

  return contract
}
