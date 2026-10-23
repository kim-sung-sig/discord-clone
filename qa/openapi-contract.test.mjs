// OpenAPI contract check (npm run openapi:check).
// docs/api/openapi.json is generated from the backend controllers by OpenApiContractTest; the backend test fails when
// it is stale. This script checks the frontend side: the committed orval client must match what orval generates
// from the committed spec, and the spec must keep the conventions the generated client relies on.
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { cpSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const generatedDir = 'packages/api-client/src/generated'
const spec = JSON.parse(readFileSync(join(root, 'docs/api/openapi.json'), 'utf8'))

assert.equal(spec.openapi, '3.1.0')
assert.equal(spec.info.title, 'Discord Clone API')
assert.equal(spec.components.securitySchemes.bearerAuth.scheme, 'bearer')

const operations = Object.values(spec.paths).flatMap((path) =>
  Object.entries(path).filter(([method]) => ['get', 'put', 'post', 'patch', 'delete'].includes(method)).map(([, op]) => op)
)
const operationIds = operations.map((operation) => operation.operationId)
assert.equal(new Set(operationIds).size, operationIds.length, 'operationId values must be unique')
for (const id of operationIds) {
  assert.match(id, /^[a-z][A-Za-z0-9]*$/, `operationId ${id} must be a plain function name`)
}

assert.ok(spec.paths['/api/auth/signup'].post.responses['201'])
assert.ok(spec.paths['/api/auth/logout'].post.responses['204'])
assert.equal(spec.paths['/api/auth/logout'].post.responses['204'].content, undefined)
assert.deepEqual([...spec.components.schemas.ApiProblem.required].sort(),
  ['type', 'title', 'status', 'detail', 'instance', 'code', 'requestId'].sort())
for (const operation of operations) {
  assert.equal(operation.responses['500'].content['application/problem+json'].schema.$ref, '#/components/schemas/ApiProblem')
}
for (const [name, schema] of Object.entries(spec.components.schemas)) {
  if (!name.endsWith('Request') && schema.properties) {
    assert.deepEqual([...(schema.required ?? [])].sort(), Object.keys(schema.properties).sort(), `${name} response presence must be explicit`)
  }
}
assert.ok(spec.components.schemas.GuildChannelResponse.properties.parentId.type.includes('null'))

for (const path of [
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/sessions',
  '/api/auth/sessions/{sessionId}',
  '/api/auth/logout',
  '/api/guilds',
  '/api/users/@me/guilds',
  '/api/channels/{channelId}/messages',
  '/api/guilds/{guildId}/channels/{channelId}/messages/{messageId}/reports',
  '/api/guilds/{guildId}/message-reports/{reportId}/resolve',
  '/api/voice/channels/{channelId}/join',
  '/api/gateway/sessions/{sessionId}/events',
  '/api/notifications',
  '/api/social/dms/{targetUserId}/messages',
  '/api/guilds/{guildId}/events/{eventId}'
]) {
  assert.ok(spec.paths[path], `${path} must be part of the generated OpenAPI contract`)
}
// The CSP report endpoint is a Nuxt server route (apps/web/server/routes/api/security), not a backend API.
assert.equal(spec.paths['/api/security/csp-report'], undefined)

// Regenerate into an ignored scratch copy (inside the repo so orval resolves from node_modules) and compare.
const scratch = join(root, 'build', 'openapi-check')
rmSync(scratch, { recursive: true, force: true })
mkdirSync(join(scratch, 'docs/api'), { recursive: true })
cpSync(join(root, 'docs/api/openapi.json'), join(scratch, 'docs/api/openapi.json'))
cpSync(join(root, 'packages/api-client/src/http'), join(scratch, 'packages/api-client/src/http'), { recursive: true })
writeFileSync(join(scratch, 'orval.config.mjs'), readFileSync(join(root, 'orval.config.mjs'), 'utf8'))
const orvalPackage = createRequire(import.meta.url).resolve('orval/package.json')
const orvalBin = JSON.parse(readFileSync(orvalPackage, 'utf8')).bin
const orvalCli = resolve(dirname(orvalPackage), typeof orvalBin === 'string' ? orvalBin : orvalBin.orval)

const listFiles = (base) => {
  const files = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry)
      if (statSync(full).isDirectory()) walk(full)
      else files.push(relative(base, full).replaceAll('\\', '/'))
    }
  }
  walk(base)
  return files.sort()
}
const normalize = (text) => text.replace(/\r\n/g, '\n')
let expectedFiles
const committedDir = join(root, generatedDir)
try {
  execFileSync(process.execPath, [orvalCli, '--config', 'orval.config.mjs'], { cwd: scratch, stdio: 'pipe' })
  const expectedDir = join(scratch, generatedDir)
  expectedFiles = listFiles(expectedDir)
  assert.deepEqual(listFiles(committedDir), expectedFiles, `${generatedDir} file set is stale; run npm run openapi:write`)
  for (const file of expectedFiles) {
    assert.equal(
      normalize(readFileSync(join(committedDir, file), 'utf8')),
      normalize(readFileSync(join(expectedDir, file), 'utf8')),
      `${generatedDir}/${file} is stale; run npm run openapi:write`
    )
  }
  // Path parameters must stay URL-encoded (urlEncodeParameters): a raw `${id}` lets an id add segments or a query.
  for (const file of expectedFiles.filter((name) => name.startsWith('endpoints/'))) {
    const source = readFileSync(join(committedDir, file), 'utf8')
    for (const template of source.match(/`\/api\/[^`]*`/g) ?? []) {
      const pathPart = template.split('?')[0]
      assert.doesNotMatch(pathPart, /\$\{(?!encodeURIComponent\()/, `${file}: unencoded path parameter in ${template}`)
    }
  }
  assert.ok(!expectedFiles.includes('endpoints/message-outbox.ts'), 'internal operator endpoints must stay out of the web client')
} finally {
  rmSync(scratch, { recursive: true, force: true })
}
console.log(`OPENAPI_CONTRACT_PASS operations=${operationIds.length} generatedFiles=${expectedFiles.length}`)
