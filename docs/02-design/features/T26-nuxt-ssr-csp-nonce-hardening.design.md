# T26 Nuxt SSR CSP Nonce Hardening Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T26 Nuxt SSR CSP Nonce Hardening

## Architecture Decision

Keep `server/utils/security-headers.ts` as the pure policy boundary, but make it accept an optional `scriptNonce`. Add `server/plugins/csp-nonce.ts` to own per-response nonce generation and HTML script-tag mutation through Nitro render hooks.

## Data Flow

1. Nitro starts rendering an HTML response.
2. The CSP nonce plugin generates a random base64url nonce.
3. The plugin adds `nonce="..."` to rendered `<script>` tags that do not already have a nonce.
4. The plugin sets HTML security headers with `script-src 'self' 'nonce-...'`.
5. Existing middleware remains as a fallback for non-rendered responses and uses a no-inline baseline.

## CSP Policy

- `script-src` should include `'self'` and a request nonce when available.
- `script-src` should not include `'unsafe-inline'`.
- `style-src 'unsafe-inline'` remains unchanged for now because the T26 risk is script execution.
- Dev websocket/connect allowances remain for Nuxt and backend local QA.

## Test Strategy

- Unit test `htmlSecurityHeaders({ scriptNonce })` verifies nonce inclusion and no script unsafe-inline.
- Unit test script tag injection helper verifies matching nonce on inline and external scripts without duplicating existing nonce attributes.
- Existing Playwright tests verify hydration still works.
- T24 real-backend harness verifies full browser/backend flow.

## Risks

- Nitro hook shape can change across Nuxt versions; helpers are tested separately to keep failures isolated.
- Middleware fallback cannot know a nonce before render; rendered HTML path is handled by plugin.
