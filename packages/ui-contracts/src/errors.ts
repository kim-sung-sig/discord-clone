export type UiErrorKind = 'auth' | 'permission' | 'network' | 'not-found' | 'unknown'

export interface UiErrorContract {
  readonly kind: UiErrorKind
  readonly surfaceTreatment: 'inline' | 'toast' | 'blocking'
  readonly retryable: boolean
}

export const errorContracts: readonly UiErrorContract[] = [
  { kind: 'auth', surfaceTreatment: 'blocking', retryable: false },
  { kind: 'permission', surfaceTreatment: 'inline', retryable: false },
  { kind: 'network', surfaceTreatment: 'toast', retryable: true },
  { kind: 'not-found', surfaceTreatment: 'inline', retryable: false },
  { kind: 'unknown', surfaceTreatment: 'toast', retryable: true }
] as const
