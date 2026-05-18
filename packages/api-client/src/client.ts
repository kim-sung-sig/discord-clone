import type { ApiErrorResponse, ApiPath } from './generated/openapi-types'

export interface ApiClientOptions {
  readonly baseUrl?: string
  readonly fetcher: typeof fetch
}

export interface ApiRequestOptions {
  readonly bearerToken?: string
  readonly body?: unknown
  readonly requestId?: string
}

export class ApiClientError extends Error {
  constructor(
    readonly status: number,
    readonly requestId: string | undefined,
    readonly code: string,
    message: string,
    readonly body: unknown
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

export const createApiClient = (options: ApiClientOptions) => {
  const request = async <T>(method: string, path: ApiPath, requestOptions: ApiRequestOptions = {}): Promise<T> => {
    const hasBody = requestOptions.body !== undefined
    const response = await options.fetcher(joinUrl(options.baseUrl, path), {
      method,
      credentials: 'include',
      headers: headersFor(requestOptions, hasBody),
      ...(hasBody ? { body: JSON.stringify(requestOptions.body) } : {})
    })

    if (response.status === 204) {
      return undefined as T
    }

    const body = await response.json().catch(() => undefined)
    if (!response.ok) {
      throw toApiClientError(response.status, body, requestOptions.requestId)
    }
    return body as T
  }

  return { request }
}

const headersFor = (options: ApiRequestOptions, hasBody: boolean): Record<string, string> => {
  const headers: Record<string, string> = {
    'X-Request-Id': options.requestId ?? createRequestId()
  }
  if (options.bearerToken) {
    headers.Authorization = `Bearer ${options.bearerToken}`
  }
  if (hasBody) {
    headers['Content-Type'] = 'application/json'
  }
  return headers
}

const toApiClientError = (status: number, body: unknown, fallbackRequestId: string | undefined): ApiClientError => {
  if (isApiErrorResponse(body)) {
    return new ApiClientError(body.status, body.requestId, body.code, body.message, body)
  }
  return new ApiClientError(status, fallbackRequestId, 'UNKNOWN_ERROR', `API request failed with ${status}`, body)
}

const isApiErrorResponse = (body: unknown): body is ApiErrorResponse => {
  if (!body || typeof body !== 'object') {
    return false
  }
  const candidate = body as Record<string, unknown>
  return typeof candidate.requestId === 'string'
    && typeof candidate.code === 'string'
    && typeof candidate.message === 'string'
    && typeof candidate.status === 'number'
}

const joinUrl = (baseUrl: string | undefined, path: string): string => {
  if (!baseUrl) {
    return path
  }
  return `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

const createRequestId = (): string => {
  const timePart = Date.now().toString(36)
  const randomPart = Math.random().toString(36).slice(2, 12)
  return `api-${timePart}-${randomPart}`
}
