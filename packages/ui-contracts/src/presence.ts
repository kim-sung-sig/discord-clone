export type PresenceStatus = 'online' | 'idle' | 'busy' | 'offline'

export interface PresenceContract {
  readonly status: PresenceStatus
  readonly showInMemberList: boolean
  readonly showInDmList: boolean
}

export const presenceContracts: readonly PresenceContract[] = [
  { status: 'online', showInMemberList: true, showInDmList: true },
  { status: 'idle', showInMemberList: true, showInDmList: true },
  { status: 'busy', showInMemberList: true, showInDmList: true },
  { status: 'offline', showInMemberList: false, showInDmList: true }
] as const
