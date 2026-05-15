import type { PlatformSurface } from '@discord-clone/ui-contracts/screens'

export type CapabilityName =
  | 'notification'
  | 'deep-link'
  | 'tray'
  | 'offline-shell'
  | 'push'
  | 'background-session'
  | 'native-file-picker'

export interface PlatformCapability {
  readonly surface: PlatformSurface
  readonly name: CapabilityName
  readonly requiredForMvp: boolean
}

export const platformCapabilities: readonly PlatformCapability[] = [
  { surface: 'pwa-mobile', name: 'offline-shell', requiredForMvp: true },
  { surface: 'desktop-app', name: 'notification', requiredForMvp: true },
  { surface: 'desktop-app', name: 'deep-link', requiredForMvp: false },
  { surface: 'desktop-app', name: 'tray', requiredForMvp: false },
  { surface: 'native-mobile', name: 'push', requiredForMvp: false },
  { surface: 'native-mobile', name: 'background-session', requiredForMvp: false },
  { surface: 'native-mobile', name: 'native-file-picker', requiredForMvp: false }
] as const

export const nativeOnlyCapabilities = platformCapabilities.filter(
  (capability) => capability.surface === 'native-mobile'
)

export function capabilitiesForSurface(surface: PlatformSurface): readonly PlatformCapability[] {
  return platformCapabilities.filter((capability) => capability.surface === surface)
}

export function requiredCapabilitiesForSurface(surface: PlatformSurface): readonly PlatformCapability[] {
  return capabilitiesForSurface(surface).filter((capability) => capability.requiredForMvp)
}
