export type NavigationMode = 'multi-pane' | 'single-pane-tabs' | 'single-pane-stack'

export type NavigationEntry = 'guild-channel' | 'deep-link-or-last-channel' | 'auth-or-last-channel'

export type SecondaryNavigationAccess = 'server-drawer' | 'channel-drawer' | 'member-sheet' | 'member-list'

export interface NavigationContract {
  readonly mode: NavigationMode
  readonly entry: NavigationEntry
  readonly secondaryAccess: readonly SecondaryNavigationAccess[]
  readonly requiresAuth: boolean
  readonly requiresPermissionCheck: boolean
  readonly fallbackOnDenied: 'login' | 'guild-list' | 'safe-home'
}
