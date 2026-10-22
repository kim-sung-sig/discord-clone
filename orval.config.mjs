import { defineConfig } from 'orval'

// docs/api/openapi.json is generated from the backend controllers (OpenApiContractTest).
// Regenerate with `npm run openapi:write`; `npm run openapi:check` fails when this output is stale.
export default defineConfig({
  discord: {
    input: {
      target: './docs/api/openapi.json'
    },
    output: {
      mode: 'tags',
      client: 'fetch',
      target: './packages/api-client/src/generated/endpoints',
      schemas: './packages/api-client/src/generated/model',
      clean: true,
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
