/**
 * Custom fetch used by the orval-generated client (orval.config.mjs). It keeps the transport contract the
 * hand-written REST client had: cookies included, X-Request-Id on every call, Bearer auth on demand, and
 * DiscordRestError for non-2xx responses.
 */
export interface DiscordRequestOptions {
  readonly baseUrl?: string
  readonly fetcher?: typeof fetch
  readonly bearerToken?: string
  readonly requestId?: string
}

export type DiscordFetchInit = RequestInit & DiscordRequestOptions

export class DiscordRestError extends Error {
  readonly detail?: string
  readonly code?: string
  readonly requestId?: string

  constructor(
    message: string,
    readonly status: number,
    readonly body: unknown
  ) {
    super(message)
    this.name = 'DiscordRestError'
    const problem = body !== null && typeof body === 'object' ? body as Record<string, unknown> : {}
    this.detail = typeof problem.detail === 'string' ? problem.detail : undefined
    this.code = typeof problem.code === 'string' ? problem.code : undefined
    this.requestId = typeof problem.requestId === 'string' ? problem.requestId : undefined
  }
}

export const discordFetch = async <T>(url: string, init: DiscordFetchInit = {}): Promise<T> => {
  const { baseUrl, fetcher, bearerToken, requestId, headers, ...requestInit } = init
  const fetchImplementation = fetcher ?? globalThis.fetch
  const response = await fetchImplementation(joinUrl(baseUrl, url), {
    ...requestInit,
    credentials: 'include',
    headers: {
      ...toRecord(headers),
      'X-Request-Id': requestId ?? createRequestId(),
      ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {})
    }
  })

  if (response.status === 204) {
    return undefined as T
  }

  const body: unknown = await response.json().catch(() => undefined)
  if (!response.ok) {
    throw new DiscordRestError(`Discord REST request failed with ${response.status}`, response.status, body)
  }
  return body as T
}

const joinUrl = (baseUrl: string | undefined, path: string): string => {
  if (!baseUrl) {
    return path
  }
  return `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

const toRecord = (headers: HeadersInit | undefined): Record<string, string> => {
  if (!headers) {
    return {}
  }
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries())
  }
  return Array.isArray(headers) ? Object.fromEntries(headers) : { ...headers }
}

const createRequestId = (): string => {
  const timePart = Date.now().toString(36)
  const randomPart = Math.random().toString(36).slice(2, 12)
  return `web-${timePart}-${randomPart}`
}
