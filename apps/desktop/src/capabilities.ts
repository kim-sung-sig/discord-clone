import { capabilitiesForSurface, type CapabilityName } from '@discord-clone/platform-shell/capabilities'
import type { PlatformSurface } from '@discord-clone/ui-contracts/screens'

export type DesktopShellCapabilityName =
  | Extract<CapabilityName, 'notification' | 'deep-link' | 'tray'>
  | 'window-state'

export type DesktopShellCapabilitySource = 'platform-shell' | 'desktop-shell'

export interface DesktopShellCapability {
  readonly name: DesktopShellCapabilityName
  readonly source: DesktopShellCapabilitySource
  readonly status: 'placeholder'
}

export interface DesktopNotificationRequest {
  readonly title: string
  readonly body?: string
}

export interface DesktopWindowState {
  readonly width: number
  readonly height: number
  readonly x?: number
  readonly y?: number
  readonly maximized?: boolean
}

export interface DesktopCapabilityResult {
  readonly capability: DesktopShellCapabilityName
  readonly status: 'placeholder'
}

export interface DesktopShellAdapter {
  readonly surface: Extract<PlatformSurface, 'desktop-app'>
  readonly capabilities: readonly DesktopShellCapability[]
  notify(request: DesktopNotificationRequest): Promise<DesktopCapabilityResult>
  openDeepLink(url: string): Promise<DesktopCapabilityResult>
  setTrayStatus(status: 'connected' | 'connecting' | 'disconnected'): Promise<DesktopCapabilityResult>
  saveWindowState(state: DesktopWindowState): Promise<DesktopCapabilityResult>
}

const sharedDesktopCapabilities = capabilitiesForSurface('desktop-app')

export const desktopShellCapabilities: readonly DesktopShellCapability[] = [
  ...sharedDesktopCapabilities.map((capability) => ({
    name: capability.name as Extract<CapabilityName, 'notification' | 'deep-link' | 'tray'>,
    source: 'platform-shell' as const,
    status: 'placeholder' as const
  })),
  {
    name: 'window-state',
    source: 'desktop-shell',
    status: 'placeholder'
  }
] as const

function placeholderResult(capability: DesktopShellCapabilityName): Promise<DesktopCapabilityResult> {
  return Promise.resolve({
    capability,
    status: 'placeholder'
  })
}

export function createDesktopShellAdapter(): DesktopShellAdapter {
  return {
    surface: 'desktop-app',
    capabilities: desktopShellCapabilities,
    notify: (_request) => placeholderResult('notification'),
    openDeepLink: (_url) => placeholderResult('deep-link'),
    setTrayStatus: (_status) => placeholderResult('tray'),
    saveWindowState: (_state) => placeholderResult('window-state')
  }
}
