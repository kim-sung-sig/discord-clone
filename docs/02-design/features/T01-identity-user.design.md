# T01 Identity/User Design

작성일: 2026-05-13  
PDCA Phase: Design

## Architecture

T01 adds two backend modules:

- `backend/modules/identity`: authentication policy and token/session primitives
- `backend/modules/user`: profile and privacy primitives

No persistence adapter is introduced yet. The goal is to lock down behavior and contracts before adding database/API code.

## Identity Model

```text
EmailAddress
PasswordHasher
BCryptPasswordHasher
AccessTokenService
AccessTokenClaims
RefreshSession
RefreshTokenService
LoginFailureTracker
```

### EmailAddress

- trims and lowercases email
- requires simple local/domain format
- rejects blank input

### PasswordHasher

- interface for hash/verify
- implementation uses BCrypt from Spring Security Crypto
- raw password must never equal stored hash

### AccessTokenService

- creates compact HMAC-SHA256 JWT-like access token
- payload includes `sub`, `iat`, `exp`
- verifies signature and expiry
- throws explicit `TokenVerificationException` for invalid/expired token

### RefreshSession

- immutable record with:
  - session id
  - user id
  - token hash
  - device name
  - created at
  - expires at
  - revoked at
- rotation returns:
  - revoked previous session
  - new active session

### LoginFailureTracker

- tracks failures per normalized email
- locks after threshold
- lock expires at configured time
- successful login clears failure state

## User Model

```text
Username
UserProfile
PrivacySettings
```

### Username

- 3-32 chars
- lowercase letters, numbers, underscore, dot
- no leading/trailing dot

### UserProfile

- id
- username
- display name
- created at
- privacy settings

### PrivacySettings

- default:
  - allow direct messages from mutual guild members
  - allow friend requests

## Testing Strategy

- Unit tests for every behavior above
- No mocks for crypto/token/session rules
- Use fixed `Clock` for expiry/lockout tests
- Run full Gradle test gate after implementation

## Next Slice

T01-B will add:

- persistence entities/repositories
- signup/login/logout REST API
- refresh cookie policy
- Nuxt login UI and e2e

