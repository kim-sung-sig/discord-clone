export type PermissionAction =
  | 'guild:view'
  | 'channel:view'
  | 'message:read'
  | 'message:send'
  | 'voice:join'
  | 'member:list'

export type PermissionVisibility = 'enabled' | 'disabled' | 'hidden'

export interface PermissionContract {
  readonly deniedVisibility: PermissionVisibility
  readonly reason: 'auth-required' | 'membership-required' | 'role-required'
}

export const permissionContractMap = {
  'guild:view': { deniedVisibility: 'hidden', reason: 'membership-required' },
  'channel:view': { deniedVisibility: 'hidden', reason: 'role-required' },
  'message:read': { deniedVisibility: 'hidden', reason: 'role-required' },
  'message:send': { deniedVisibility: 'disabled', reason: 'role-required' },
  'voice:join': { deniedVisibility: 'disabled', reason: 'role-required' },
  'member:list': { deniedVisibility: 'hidden', reason: 'membership-required' }
} as const satisfies Record<PermissionAction, PermissionContract>

export const permissionContracts = Object.entries(permissionContractMap).map(([action, contract]) => ({
  action: action as PermissionAction,
  ...contract
}))
