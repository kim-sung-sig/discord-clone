import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const specPath = resolve(root, 'docs/api/openapi.json')
const typesPath = resolve(root, 'packages/api-client/src/generated/openapi-types.ts')

const apiErrorResponse = {
  type: 'object',
  required: ['requestId', 'code', 'message', 'status'],
  properties: {
    requestId: { type: 'string' },
    code: { type: 'string' },
    message: { type: 'string' },
    status: { type: 'integer', format: 'int32' }
  },
  additionalProperties: false
}

const uuid = { type: 'string', format: 'uuid' }
const requestIdHeader = {
  name: 'X-Request-Id',
  in: 'header',
  required: false,
  schema: { type: 'string' },
  description: 'Safe request correlation id. Unsafe incoming values are replaced by the backend.'
}
const authHeader = {
  name: 'Authorization',
  in: 'header',
  required: false,
  schema: { type: 'string' },
  description: 'Bearer access token where authentication is required.'
}

const jsonBody = (schemaRef) => ({
  required: true,
  content: {
    'application/json': {
      schema: schemaRef
    }
  }
})

const standardResponses = {
  badRequest: { $ref: '#/components/responses/BadRequest' },
  unauthorized: { $ref: '#/components/responses/Unauthorized' },
  forbidden: { $ref: '#/components/responses/Forbidden' },
  notFound: { $ref: '#/components/responses/NotFound' }
}

const operation = ({ operationId, tags, summary, requestBody, responses = {}, parameters = [] }) => ({
  operationId,
  tags,
  summary,
  parameters: [requestIdHeader, ...parameters],
  ...(requestBody ? { requestBody } : {}),
  responses: {
    ...responses,
    400: standardResponses.badRequest,
    401: standardResponses.unauthorized,
    403: standardResponses.forbidden,
    404: standardResponses.notFound
  }
})

const spec = {
  openapi: '3.1.0',
  info: {
    title: 'Discord Clone API',
    version: '0.1.0'
  },
  servers: [{ url: 'http://127.0.0.1:8080' }],
  tags: [
    { name: 'auth' },
    { name: 'guilds' },
    { name: 'messages' },
    { name: 'moderation' },
    { name: 'voice' },
    { name: 'gateway' },
    { name: 'security' }
  ],
  paths: {
    '/api/auth/login': {
      post: operation({
        operationId: 'login',
        tags: ['auth'],
        summary: 'Login and issue an access token.',
        requestBody: jsonBody({ $ref: '#/components/schemas/LoginRequest' }),
        responses: { 200: { $ref: '#/components/responses/AuthSession' } }
      })
    },
    '/api/auth/logout': {
      post: operation({
        operationId: 'logout',
        tags: ['auth'],
        summary: 'Logout the current session.',
        parameters: [authHeader],
        responses: { 204: { description: 'Logged out.' } }
      })
    },
    '/api/guilds': {
      post: operation({
        operationId: 'createGuild',
        tags: ['guilds'],
        summary: 'Create a guild.',
        parameters: [authHeader],
        requestBody: jsonBody({ $ref: '#/components/schemas/CreateGuildRequest' }),
        responses: { 201: { $ref: '#/components/responses/Guild' } }
      })
    },
    '/api/users/@me/guilds': {
      get: operation({
        operationId: 'listCurrentUserGuilds',
        tags: ['guilds'],
        summary: 'List guilds and visible channels for the authenticated user shell.',
        parameters: [authHeader],
        responses: { 200: { $ref: '#/components/responses/UserGuildList' } }
      })
    },
    '/api/guilds/{guildId}/channels': {
      post: operation({
        operationId: 'createChannel',
        tags: ['guilds'],
        summary: 'Create a guild channel.',
        parameters: [authHeader, { name: 'guildId', in: 'path', required: true, schema: uuid }],
        requestBody: jsonBody({ $ref: '#/components/schemas/CreateChannelRequest' }),
        responses: { 201: { $ref: '#/components/responses/Channel' } }
      })
    },
    '/api/channels/{channelId}/messages': {
      get: operation({
        operationId: 'listMessages',
        tags: ['messages'],
        summary: 'List channel messages.',
        parameters: [
          authHeader,
          { name: 'channelId', in: 'path', required: true, schema: uuid },
          { name: 'before', in: 'query', required: false, schema: { type: 'string' } },
          { name: 'limit', in: 'query', required: false, schema: { type: 'integer', minimum: 1, maximum: 100 } }
        ],
        responses: { 200: { $ref: '#/components/responses/MessageList' } }
      }),
      post: operation({
        operationId: 'createMessage',
        tags: ['messages'],
        summary: 'Create a channel message.',
        parameters: [authHeader, { name: 'channelId', in: 'path', required: true, schema: uuid }],
        requestBody: jsonBody({ $ref: '#/components/schemas/CreateMessageRequest' }),
        responses: { 201: { $ref: '#/components/responses/Message' } }
      })
    },
    '/api/guilds/{guildId}/channels/{channelId}/messages/{messageId}/reports': {
      post: operation({
        operationId: 'reportMessage',
        tags: ['moderation'],
        summary: 'Report a visible channel message for moderator review.',
        parameters: [
          authHeader,
          { name: 'guildId', in: 'path', required: true, schema: uuid },
          { name: 'channelId', in: 'path', required: true, schema: uuid },
          { name: 'messageId', in: 'path', required: true, schema: uuid }
        ],
        requestBody: jsonBody({ $ref: '#/components/schemas/ReportMessageRequest' }),
        responses: { 201: { $ref: '#/components/responses/MessageReport' } }
      })
    },
    '/api/guilds/{guildId}/message-reports': {
      get: operation({
        operationId: 'listMessageReports',
        tags: ['moderation'],
        summary: 'List pending message reports for moderators.',
        parameters: [
          authHeader,
          { name: 'guildId', in: 'path', required: true, schema: uuid },
          { name: 'limit', in: 'query', required: false, schema: { type: 'integer', minimum: 1, maximum: 100 } }
        ],
        responses: { 200: { $ref: '#/components/responses/MessageReportList' } }
      })
    },
    '/api/guilds/{guildId}/message-reports/{reportId}/resolve': {
      post: operation({
        operationId: 'resolveMessageReport',
        tags: ['moderation'],
        summary: 'Resolve or dismiss a pending message report.',
        parameters: [
          authHeader,
          { name: 'guildId', in: 'path', required: true, schema: uuid },
          { name: 'reportId', in: 'path', required: true, schema: uuid }
        ],
        requestBody: jsonBody({ $ref: '#/components/schemas/ResolveMessageReportRequest' }),
        responses: { 200: { $ref: '#/components/responses/MessageReport' } }
      })
    },
    '/api/voice/channels/{channelId}/join': {
      post: operation({
        operationId: 'joinVoiceChannel',
        tags: ['voice'],
        summary: 'Join a voice channel and receive a media token.',
        parameters: [authHeader, { name: 'channelId', in: 'path', required: true, schema: uuid }],
        responses: { 200: { $ref: '#/components/responses/VoiceJoin' } }
      })
    },
    '/api/gateway/sessions/{sessionId}/events': {
      get: operation({
        operationId: 'listGatewayEvents',
        tags: ['gateway'],
        summary: 'Poll Gateway events after a sequence.',
        parameters: [
          authHeader,
          { name: 'sessionId', in: 'path', required: true, schema: uuid },
          { name: 'afterSeq', in: 'query', required: false, schema: { type: 'integer', format: 'int64', minimum: 0 } }
        ],
        responses: { 200: { $ref: '#/components/responses/GatewayEvents' } }
      })
    },
    '/api/security/csp-report': {
      post: operation({
        operationId: 'submitCspReport',
        tags: ['security'],
        summary: 'Submit sanitized CSP violation telemetry.',
        requestBody: jsonBody({ type: 'object', additionalProperties: true }),
        responses: { 204: { description: 'Report accepted.' } }
      })
    },
    '/api/admin/global-roles/audit-log': {
      get: operation({
        operationId: 'listGlobalRoleAuditLog',
        tags: ['security'],
        summary: 'List global admin role audit entries.',
        parameters: [
          authHeader,
          { name: 'targetUserId', in: 'query', required: false, schema: uuid },
          { name: 'limit', in: 'query', required: false, schema: { type: 'integer', minimum: 1, maximum: 100 } }
        ],
        responses: { 200: { $ref: '#/components/responses/GlobalRoleAuditLog' } }
      })
    }
  },
  components: {
    schemas: {
      ApiErrorResponse: apiErrorResponse,
      LoginRequest: {
        type: 'object',
        required: ['email', 'password'],
        properties: { email: { type: 'string', format: 'email' }, password: { type: 'string' } }
      },
      CreateGuildRequest: {
        type: 'object',
        required: ['name'],
        properties: { name: { type: 'string' } }
      },
      CreateChannelRequest: {
        type: 'object',
        required: ['name', 'type'],
        properties: { name: { type: 'string' }, type: { type: 'string' }, parentId: { ...uuid, nullable: true } }
      },
      CreateMessageRequest: {
        type: 'object',
        required: ['content', 'idempotencyKey'],
        properties: { content: { type: 'string' }, idempotencyKey: { type: 'string' }, clientEventId: { type: 'string' } }
      },
      ReportMessageRequest: {
        type: 'object',
        required: ['reason'],
        properties: { reason: { type: 'string', minLength: 1 } }
      },
      ResolveMessageReportRequest: {
        type: 'object',
        required: ['status'],
        properties: {
          status: { type: 'string', enum: ['RESOLVED', 'DISMISSED'] },
          resolution: { type: 'string' }
        }
      }
    },
    responses: Object.fromEntries(
      [
        ['BadRequest', 400],
        ['Unauthorized', 401],
        ['Forbidden', 403],
        ['NotFound', 404]
      ].map(([name, status]) => [
        name,
        {
          description: `${status} error.`,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/ApiErrorResponse' } } }
        }
      ])
    )
  }
}

for (const [name, description] of [
  ['AuthSession', 'Authenticated session response.'],
  ['Guild', 'Guild response.'],
  ['UserGuildList', 'Authenticated user guild list response.'],
  ['Channel', 'Channel response.'],
  ['Message', 'Message response.'],
  ['MessageList', 'Message list response.'],
  ['MessageReport', 'Message report response.'],
  ['MessageReportList', 'Pending message report list response.'],
  ['VoiceJoin', 'Voice join response.'],
  ['GatewayEvents', 'Gateway events response.'],
  ['GlobalRoleAuditLog', 'Global role audit log response.']
]) {
  spec.components.responses[name] = {
    description,
    content: { 'application/json': { schema: { type: 'object', additionalProperties: true } } }
  }
}

const sortedSpec = sortObject(spec)
const renderedSpec = `${JSON.stringify(sortedSpec, null, 2)}\n`
const paths = Object.keys(sortedSpec.paths)
const renderedTypes = `// Generated by qa/openapi-contract.mjs. Do not edit by hand.

export interface ApiErrorResponse {
  readonly requestId: string
  readonly code: string
  readonly message: string
  readonly status: number
}

export type ApiPath =
${paths.map((path) => `  | '${path}'`).join('\n')}

export type ApiMethod = 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT'

export interface ApiOperation {
  readonly method: ApiMethod
  readonly path: ApiPath
  readonly operationId: string
}

export const apiOperations = ${JSON.stringify(operations(sortedSpec), null, 2)} as const satisfies readonly ApiOperation[]
`

const mode = process.argv[2] ?? '--check'
if (!['--check', '--write'].includes(mode)) {
  throw new Error('Usage: node qa/openapi-contract.mjs [--check|--write]')
}

if (mode === '--write') {
  write(specPath, renderedSpec)
  write(typesPath, renderedTypes)
} else {
  assertFresh(specPath, renderedSpec)
  assertFresh(typesPath, renderedTypes)
}

function write(filePath, content) {
  mkdirSync(dirname(filePath), { recursive: true })
  writeFileSync(filePath, content)
}

function assertFresh(filePath, expected) {
  const current = readFileSync(filePath, 'utf8')
  if (normalizeLineEndings(current) !== normalizeLineEndings(expected)) {
    throw new Error(`${filePath} is stale. Run node qa/openapi-contract.mjs --write`)
  }
}

function normalizeLineEndings(value) {
  return value.replace(/\r\n/g, '\n')
}

function operations(openApiSpec) {
  const result = []
  for (const [path, pathItem] of Object.entries(openApiSpec.paths)) {
    for (const method of ['delete', 'get', 'patch', 'post', 'put']) {
      if (pathItem[method]) {
        result.push({ method: method.toUpperCase(), path, operationId: pathItem[method].operationId })
      }
    }
  }
  return result
}

function sortObject(value) {
  if (Array.isArray(value)) {
    return value.map(sortObject)
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, child]) => [key, sortObject(child)])
  )
}
