# T34 Session & Account Security Hardening Plan

## Problem
Authentication still relied on browser-visible session storage for access token restoration. Refresh sessions were not persisted, rotated, listed, or revocable, which leaves account takeover and stolen refresh-token reuse paths insufficiently controlled.

## Plan
1. Persist refresh sessions with token hashes, device names, expiry, and revocation state.
2. Rotate refresh tokens on every refresh and revoke the session family when reuse is detected.
3. Expose session list and self-service revoke APIs.
4. Move browser policy to memory-only access tokens plus httpOnly refresh cookie.
5. Add frontend session management controls in the user panel.
6. Emit a suspicious-login candidate event when a login appears from a new device.
7. Cover API, persistence, browser storage, and CORS credential behavior with tests.

## Success Criteria
- Refresh token reuse returns 401 and revokes related refresh sessions.
- Logout and explicit session revoke invalidate refresh sessions.
- Users can list and revoke their own sessions.
- Frontend does not persist access tokens in localStorage or sessionStorage.
- Cross-origin Nuxt development calls support refresh cookies through `credentials: include`.
- Suspicious new-device login leaves a structured audit/log candidate.

## Failure Criteria
- A rotated refresh token can be reused.
- Refresh still works after logout or self-service revoke.
- Access tokens appear in localStorage, sessionStorage, document cookies, or Playwright-visible cookies.
- Session APIs expose or revoke another user's sessions.
