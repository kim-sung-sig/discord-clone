type QueryValue = string | number | boolean | null | undefined

export interface DiscordRestClientOptions {
  baseUrl?: string
  fetch?: typeof fetch
  fetcher?: typeof fetch
}

export interface DiscordRequestOptions {
  bearerToken?: string
}

export class DiscordRestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body: unknown
  ) {
    super(message)
    this.name = 'DiscordRestError'
  }
}

const segment = (value: string): string => encodeURIComponent(value)

const withQuery = (path: string, query: Record<string, QueryValue>): string => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) {
      params.append(key, String(value))
    }
  }
  const queryString = params.toString()
  return queryString ? `${path}?${queryString}` : path
}

const authPaths = {
  signup: () => '/api/auth/signup',
  login: () => '/api/auth/login',
  logout: () => '/api/auth/logout',
  me: () => '/api/users/@me'
} as const

const guildPaths = Object.assign(
  (guildId: string) => `/api/guilds/${segment(guildId)}`,
  {
    create: () => '/api/guilds',
    createChannel: (guildId: string) => `/api/guilds/${segment(guildId)}/channels`,
    visibleChannels: (guildId: string, memberId: string) =>
      withQuery(`/api/guilds/${segment(guildId)}/channels/visible`, { memberId }),
    roles: (guildId: string) => `/api/guilds/${segment(guildId)}/roles`,
    createRole: (guildId: string) => `/api/guilds/${segment(guildId)}/roles`,
    rolePermissions: (guildId: string, roleId: string) =>
      `/api/guilds/${segment(guildId)}/roles/${segment(roleId)}/permissions`,
    member: (guildId: string, memberId: string) =>
      `/api/guilds/${segment(guildId)}/members/${segment(memberId)}`,
    memberRole: (guildId: string, memberId: string, roleId: string) =>
      `/api/guilds/${segment(guildId)}/members/${segment(memberId)}/roles/${segment(roleId)}`,
    channelRoleOverwrite: (guildId: string, channelId: string, roleId: string) =>
      `/api/guilds/${segment(guildId)}/channels/${segment(channelId)}/overwrites/roles/${segment(roleId)}`
  }
)

const channelPaths = {
  messages: (channelId: string, query: { before?: string; limit?: number } = {}) =>
    withQuery(`/api/channels/${segment(channelId)}/messages`, query),
  message: (channelId: string, messageId: string) =>
    `/api/channels/${segment(channelId)}/messages/${segment(messageId)}`,
  messagePin: (channelId: string, messageId: string) =>
    `/api/channels/${segment(channelId)}/messages/${segment(messageId)}/pin`,
  searchMessages: (channelId: string, query: { q: string; limit?: number }) =>
    withQuery(`/api/channels/${segment(channelId)}/messages/search`, query)
} as const

const invitePaths = {
  create: (guildId: string) => `/api/guilds/${segment(guildId)}/invites`,
  preview: (code: string) => `/api/invites/${segment(code)}`,
  accept: (code: string) => `/api/invites/${segment(code)}/accept`,
  delete: (code: string) => `/api/invites/${segment(code)}`
} as const

const gatewayPaths = {
  identify: () => '/api/gateway/identify',
  heartbeat: (sessionId: string) => `/api/gateway/sessions/${segment(sessionId)}/heartbeat`,
  resume: (sessionId: string) => `/api/gateway/sessions/${segment(sessionId)}/resume`,
  sessionEvents: (sessionId: string, afterSeq?: number) =>
    withQuery(`/api/gateway/sessions/${segment(sessionId)}/events`, { afterSeq }),
  events: (sessionId: string, afterSequence?: number) =>
    withQuery('/api/gateway/events', { sessionId, afterSequence }),
  publish: () => '/api/gateway/events'
} as const

export const discordApiPaths = {
  authLogin: authPaths.login,
  channelMessages: channelPaths.messages,
  invitePreview: invitePaths.preview,
  gatewayEvents: gatewayPaths.events,
  auth: authPaths,
  guild: guildPaths,
  channel: channelPaths,
  invite: invitePaths,
  gateway: gatewayPaths
} as const

const joinUrl = (baseUrl: string | undefined, path: string): string => {
  if (!baseUrl) {
    return path
  }
  return `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

const headersFor = (options: DiscordRequestOptions, hasBody: boolean): Record<string, string> => {
  const headers: Record<string, string> = {}
  if (options.bearerToken) {
    headers.Authorization = `Bearer ${options.bearerToken}`
  }
  if (hasBody) {
    headers['Content-Type'] = 'application/json'
  }
  return headers
}

export const createDiscordRestClient = (options: DiscordRestClientOptions) => {
  const fetchImplementation = options.fetcher ?? options.fetch
  if (!fetchImplementation) {
    throw new TypeError('createDiscordRestClient requires fetch or fetcher')
  }

  const request = async <T>(
    method: string,
    path: string,
    body?: unknown,
    requestOptions: DiscordRequestOptions = {}
  ): Promise<T> => {
    const hasBody = body !== undefined
    const response = await fetchImplementation(joinUrl(options.baseUrl, path), {
      method,
      headers: headersFor(requestOptions, hasBody),
      ...(hasBody ? { body: JSON.stringify(body) } : {})
    })

    if (response.status === 204) {
      return undefined as T
    }

    const responseBody = await response.json().catch(() => undefined)
    if (!response.ok) {
      throw new DiscordRestError(`Discord REST request failed with ${response.status}`, response.status, responseBody)
    }

    return responseBody as T
  }

  return {
    get: <T>(path: string, requestOptions?: DiscordRequestOptions) =>
      request<T>('GET', path, undefined, requestOptions),
    post: <T>(path: string, body?: unknown, requestOptions?: DiscordRequestOptions) =>
      request<T>('POST', path, body, requestOptions),
    patch: <T>(path: string, body?: unknown, requestOptions?: DiscordRequestOptions) =>
      request<T>('PATCH', path, body, requestOptions),
    put: <T>(path: string, body?: unknown, requestOptions?: DiscordRequestOptions) =>
      request<T>('PUT', path, body, requestOptions),
    delete: <T>(path: string, requestOptions?: DiscordRequestOptions) =>
      request<T>('DELETE', path, undefined, requestOptions)
  }
}
