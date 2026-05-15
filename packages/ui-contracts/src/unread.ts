export type UnreadScope = 'guild' | 'channel' | 'thread' | 'dm'

export interface UnreadContract {
  readonly scope: UnreadScope
  readonly badge: 'dot' | 'count' | 'mention-count'
  readonly resetsOnOpen: boolean
}

export const unreadContracts: readonly UnreadContract[] = [
  { scope: 'guild', badge: 'dot', resetsOnOpen: false },
  { scope: 'channel', badge: 'count', resetsOnOpen: true },
  { scope: 'thread', badge: 'count', resetsOnOpen: true },
  { scope: 'dm', badge: 'mention-count', resetsOnOpen: true }
] as const
