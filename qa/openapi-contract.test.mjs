import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const spec = JSON.parse(readFileSync(new URL('../docs/api/openapi.json', import.meta.url), 'utf8'))
const generatedTypes = readFileSync(
  new URL('../packages/api-client/src/generated/openapi-types.ts', import.meta.url),
  'utf8'
)

assert.equal(spec.openapi, '3.1.0')
assert.equal(spec.info.title, 'Discord Clone API')
assert.ok(spec.components.schemas.ApiErrorResponse)
assert.deepEqual(Object.keys(spec.components.schemas.ApiErrorResponse.properties).sort(), [
  'code',
  'message',
  'requestId',
  'status'
])

for (const path of [
  '/api/auth/login',
  '/api/guilds',
  '/api/users/@me/guilds',
  '/api/channels/{channelId}/messages',
  '/api/voice/channels/{channelId}/join',
  '/api/gateway/sessions/{sessionId}/events',
  '/api/security/csp-report'
]) {
  assert.ok(spec.paths[path], `${path} must be part of the OpenAPI contract`)
}

assert.match(generatedTypes, /export interface ApiErrorResponse/)
assert.match(generatedTypes, /export type ApiPath =/)
assert.match(generatedTypes, /'\/api\/auth\/login'/)
assert.match(generatedTypes, /'\/api\/users\/@me\/guilds'/)
assert.match(generatedTypes, /'\/api\/channels\/\{channelId\}\/messages'/)
