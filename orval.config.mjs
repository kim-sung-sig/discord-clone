import { defineConfig } from 'orval'

// docs/api/openapi.json is generated from the backend controllers (OpenApiContractTest).
// Regenerate with `npm run openapi:write`; `npm run openapi:check` fails when this output is stale.
export default defineConfig({
  discord: {
    input: {
      target: './docs/api/openapi.json',
      // Internal operator endpoints (X-Internal-* headers) are not part of the web client.
      filters: {
        mode: 'exclude',
        tags: ['message-outbox']
      }
    },
    output: {
      mode: 'tags',
      client: 'fetch',
      target: './packages/api-client/src/generated/endpoints',
      schemas: './packages/api-client/src/generated/model',
      clean: true,
      // Encode path parameters (the hand-written client used encodeURIComponent for every segment).
      urlEncodeParameters: true,
      override: {
        mutator: {
          path: './packages/api-client/src/http/discord-fetch.ts',
          name: 'discordFetch'
        },
        fetch: {
          includeHttpResponseReturnType: false
        }
      }
    }
  }
})
