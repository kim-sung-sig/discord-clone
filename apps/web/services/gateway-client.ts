import type { ShellGatewayEvent } from '../stores/shell'

export interface GatewayDispatch {
  sequence: number
  type: string
  guildId?: string
  channelId?: string
  payload: Record<string, unknown>
  createdAt: string
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
  const type = value.type
  const payload = value.payload
  const createdAt = value.createdAt
  const guildId = optionalString(value.guildId)
  const channelId = optionalString(value.channelId)

  if (!Number.isInteger(sequence) || sequence < 1) {
    return null
  }
  if (typeof type !== 'string' || type.trim().length === 0) {
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

  return {
    sequence,
    type: type.trim().toUpperCase(),
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
