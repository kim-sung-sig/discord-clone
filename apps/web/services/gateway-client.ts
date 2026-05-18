import type { ShellGatewayEvent } from '../stores/shell'

export interface GatewayDispatch {
  sequence: number
  type: string
  guildId?: string
  channelId?: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface GatewaySocketLike {
  onopen: (() => void) | null
  onmessage: ((event: { data: string }) => void) | null
  onclose: (() => void) | null
  send: (frame: string) => void
  close: () => void
}

export interface GatewaySocketLifecycleOptions {
  url: string
  accessToken: string
  sessionId?: () => string | null | undefined
  lastSequence: () => number
  heartbeatIntervalMs?: number
  webSocketFactory?: (url: string) => GatewaySocketLike
  onDispatch: (dispatch: GatewayDispatch) => void
  onDisconnect?: () => void
}

export interface GatewaySocketLifecycle {
  connect: () => void
  disconnect: () => void
  sendHeartbeat: () => void
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const optionalString = (value: unknown): string | undefined => {
  if (value === undefined || value === null) {
    return undefined
  }
  return typeof value === 'string' ? value : undefined
}

export const normalizeGatewayDispatch = (value: unknown): GatewayDispatch | null => {
  if (!isRecord(value)) {
    return null
  }

  const sequence = value.sequence
  const rawType = value.type
  const eventType = optionalString(value.eventType)
  const payload = value.payload ?? {}
  const createdAt = value.createdAt
  const guildId = optionalString(value.guildId)
  const channelId = optionalString(value.channelId)

  if (!Number.isInteger(sequence) || sequence < 1) {
    return null
  }
  if (typeof rawType !== 'string' || rawType.trim().length === 0) {
    return null
  }
  if (!isRecord(payload)) {
    return null
  }
  if (createdAt !== undefined && (typeof createdAt !== 'string' || createdAt.trim().length === 0)) {
    return null
  }
  if ((value.guildId !== undefined && value.guildId !== null && guildId === undefined) ||
    (value.channelId !== undefined && value.channelId !== null && channelId === undefined)) {
    return null
  }

  const normalizedType = rawType.trim().toUpperCase()
  const dispatchType = normalizedType === 'EVENT' && eventType
    ? eventType.trim().toUpperCase()
    : normalizedType

  return {
    sequence,
    type: dispatchType,
    ...(guildId ? { guildId } : {}),
    ...(channelId ? { channelId } : {}),
    payload: { ...payload },
    createdAt: createdAt ?? '1970-01-01T00:00:00.000Z'
  }
}

export const isGatewayDispatch = (value: unknown): value is GatewayDispatch =>
  normalizeGatewayDispatch(value) !== null

export const toShellGatewayEvent = (dispatch: GatewayDispatch): ShellGatewayEvent => ({
  sequence: dispatch.sequence,
  type: dispatch.type,
  label: `${dispatch.type.toLowerCase().replaceAll('_', ' ')} dispatch accepted`
})

export const createGatewaySocketLifecycle = (options: GatewaySocketLifecycleOptions): GatewaySocketLifecycle => {
  let socket: GatewaySocketLike | null = null
  let heartbeatId: ReturnType<typeof setInterval> | null = null
  const heartbeatIntervalMs = options.heartbeatIntervalMs ?? 30_000

  const sendFrame = (frame: Record<string, unknown>) => {
    socket?.send(JSON.stringify(frame))
  }

  const sendHeartbeat = () => {
    sendFrame({
      op: 'HEARTBEAT',
      lastSequence: options.lastSequence()
    })
  }

  const connect = () => {
    const factory = options.webSocketFactory ?? ((url: string) => new WebSocket(url) as GatewaySocketLike)
    socket = factory(options.url)
    socket.onopen = () => {
      const sessionId = options.sessionId?.()
      const lastSequence = options.lastSequence()
      if (sessionId && lastSequence > 0) {
        sendFrame({
          op: 'RESUME',
          token: options.accessToken,
          sessionId,
          lastSequence
        })
      } else {
        sendFrame({
          op: 'IDENTIFY',
          token: options.accessToken
        })
      }

      if (heartbeatIntervalMs > 0) {
        heartbeatId = setInterval(sendHeartbeat, heartbeatIntervalMs)
      }
    }
    socket.onmessage = (event) => {
      try {
        const dispatch = normalizeGatewayDispatch(JSON.parse(event.data))
        if (dispatch) {
          options.onDispatch(dispatch)
        }
      } catch {
        // Ignore malformed frames; the next valid Gateway frame can still advance the stream.
      }
    }
    socket.onclose = () => {
      if (heartbeatId) {
        clearInterval(heartbeatId)
        heartbeatId = null
      }
      options.onDisconnect?.()
    }
  }

  const disconnect = () => {
    if (heartbeatId) {
      clearInterval(heartbeatId)
      heartbeatId = null
    }
    socket?.close()
    socket = null
  }

  return {
    connect,
    disconnect,
    sendHeartbeat
  }
}
