# T32 Regression Architecture Review Feedback

## Scope
- Backend: architecture, module boundaries, validation, event propagation, TDD, dependency pollution, security.
- Frontend: Gateway API contract, server middleware/security headers, auth state recovery, failure messaging.

## Findings And Actions
| Area | Finding | Action |
| --- | --- | --- |
| Backend event-driven security | Public `POST /api/gateway/events` let authenticated guild members forge gateway events. | Restricted publish to an internal publisher token header and kept user bearer authorization for test adapter ownership checks. |
| Backend validation | Gateway request records used manual null checks only. | Added `spring-boot-starter-validation`, `@Valid`, and record component constraints. |
| Backend session lifecycle | Gateway timeout closure existed only as a service method. | Added scheduled `GatewaySessionMaintenance` and enabled scheduling. |
| Backend dependency pollution | `identity` domain module contained a Spring Security BCrypt adapter. | Moved BCrypt implementation into `boot/auth`, kept `PasswordHasher` as the domain port, and added an architecture regression test for Spring/Jakarta imports under modules. |
| Backend rules | DDD, validation, record/Lombok, TDD, event-driven, and security rules were implicit. | Added `backend/AGENTS.md` as the backend rule file. |
| Frontend Gateway contract | `gatewayEvents` pointed at the publish endpoint instead of session event polling. | Rewired alias to `/api/gateway/sessions/{sessionId}/events?afterSeq=` and updated contract tests. |
| Frontend security middleware | CSP only allowed local connections and Permissions-Policy blocked microphone. | Added configurable `connectSources`, runtime API origin inclusion, and `microphone=(self)`. |
| Frontend auth recovery | Auth state was memory-only. | Added sessionStorage-based tab-scoped restore, global auth middleware hydration, and updated user-facing copy/tests. |
| Frontend failure handling | API errors discarded backend message details. | Included backend error message details in shell API failure messages. |

## Residual Architecture Risks
- Controllers still depend on concrete in-memory services in several modules. The next backend refactor should introduce application ports/use cases before persistent adapters expand further.
- Gateway delivery is still polling-based. SSE/WebSocket transport should be introduced before high-volume realtime QA.
- Saga/process-manager boundaries are not formalized yet. Multi-step flows such as invite acceptance with role grants and voice/stage orchestration should move into explicit application services.

## Verification
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.GatewayControllerTest --no-daemon`
- `npm test -w apps/web -- shell-contracts.test.ts security-headers.test.ts login-form.test.ts --run`
- `.\gradlew.bat :backend:boot:test --no-daemon`
- `npm run build -w apps/web`
- `npm test -w apps/web -- --run`
- `.\gradlew.bat test --no-daemon`
- `$env:NUXT_DEV_PORT='3101'; npm run e2e -w apps/web -- login.spec.ts`
