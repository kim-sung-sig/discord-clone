# Post-T106 Residual Task Priority

Date: 2026-05-21
Scope: Residual tasks discovered from T32, T39, T40, T51, T52, T53, T54, T59, T60, T61, T62, T63, T99, T100, T102, T103, T104, T105, T106, T107, T108, T110, T111, T112, T113, T114, T116, T117, T118, T119, T120, T121, T122, T123, T124, T125, T126, T127, T128, T129, T130, T131, T132, T133, T134, T135, T136, T137, T138, T139, T140, T141, T142, T143, T144, T145, T146, T147, T148, T150, T151, T152, T153, T154, T155, T156, T157, T158, T159, T160, T161, T162, T164, T165, T169, T177, T178, T182, T183, and core Discord browser review feedback.

## Prioritization Rules

1. Protect newly exposed operator/security surfaces first.
2. Make telemetry durable, bounded, and actionable before adding more visualization.
3. Harden distributed realtime paths before product features that depend on realtime reliability.
4. Keep recovery/backup tasks high because persistence is now a project foundation.
5. Defer UI polish and charts until data ownership, access control, and retention are stable.
6. Current user direction: prioritize centralization before operator docs or feature polish.
7. Current user direction on 2026-05-20: verify T00-T49 core gaps first, then continue all operations/security work with recurring security review.
8. Current user direction on 2026-05-21: commit and push at practical task boundaries after verification.

## Reordered Queue

| Rank | Task | Priority | Source | Reason |
| ---: | --- | --- | --- | --- |
| 1 | T55 restore snapshot hash comparison | P1 | T39 | Backup drill should compare restored pre-existing data, not only run post-restore API smoke. |
| 2 | T58 production backup runbook | P1 | T39 | Local restore drill exists; production PITR/cloud procedure remains missing. |
| 3 | T56 target database lifecycle automation | P2 | T39 | Restore drill still expects operator-created target database. |
| 4 | T57 process-tree cleanup helper for QA harnesses | P2 | T39 | Failed startup can leave child Java processes if cleanup is interrupted. |
| 5 | T189 Playwright local port isolation guard | P2 | T166 | Visual smoke can silently reuse an unrelated localhost:3000 service unless an isolated port/CI mode is enforced. |
| 6 | T163 Remove Legacy Frontend SBOM Fallback Utility | P3 | T162 | The security gate no longer invokes the fallback generator; removing unused tooling later would reduce maintenance surface. |
| 7 | T167 CSP alert incident lifecycle history | P3 | T127 | Current acknowledgement stores latest state per fingerprint; a later incident model should keep assignment, status changes, and exportable history. |
| 8 | T168 Privacy-reviewed subject distribution summary | P3 | T128 | Operators may later need aggregate subject distribution, but it needs privacy review before exposing more identifiers. |
| 9 | T170 Operator token audit review UI | P3 | T129 | Audit entries exist in the store contract but are not yet reviewable from the dashboard. |
| 10 | T171 Operator token retention and pruning policy | P3 | T169 | Durable token/audit rows should eventually have retention rules and cleanup automation. |
| 11 | T172 Redis CSP limiter lifecycle alert thresholds | P3 | T131 | Lifecycle metrics are now visible; thresholds can later highlight repeated fail-closed or reconnect churn. |
| 12 | T173 Global admin audit archive and legal hold workflow | P3 | T133 | Retention policy is visible, but archival automation and legal hold are still deferred. |
| 13 | T174 Backend auth probe timeout and alert policy | P3 | T140 | Reachability is visible now; timeout tuning and alert thresholds should be explicit before production alerting. |
| 14 | T175 Admin CLI custom smoke fixture non-mutating mode | P3 | T142 | Explicit `-SmokeUserId` can still seed/update caller-provided fixtures; a future mode should verify existing users without mutation or require stronger confirmation. |
| 15 | T176 Admin CLI NOOP BootRun Smoke Coverage | P3 | T143 | Duplicate grant and missing-role revoke are unit-tested, but the real bootRun smoke covers only APPLIED mutation paths. |
| 16 | T179 Kafka DLQ monitoring rule deployment | P3 | T178 | Runtime metrics exist, but Prometheus/Grafana/PagerDuty rule deployment is environment-specific and still needs production ownership. |
| 17 | T180 CSP directive trend breakdown | P3 | T107 | Aggregate trend is visible now; later directive-level trends could distinguish style regressions from script regressions without exposing raw reports. |
| 18 | T181 LiveKit media smoke CI service automation | P3 | T165 | The real media smoke is runnable and locally verified, but CI currently enforces the contract only. |
| 19 | T184 Redis Gateway DLQ metric export and alert deployment | P3 | T63 | In-process DLQ counters and runbook exist; production monitoring rule deployment is environment-specific. |
| 20 | T185 Gateway session TTL observability | P3 | T182 | TTL cleanup works, but operators may later need aggregate stale-prune counters and dashboard/metrics visibility. |
| 21 | T186 Gateway subscription reconciliation metrics | P3 | T62 | Reconciliation is implemented, but aggregate counters for re-subscribed guilds/channels could help diagnose churn later. |
| 22 | T187 Redis Gateway own-source skip observability | P3 | T183 | Same-node duplicate suppression is implemented, but aggregate skipped-own-record counters could help diagnose local publish/poll churn later. |
| 23 | T188 Real backend Playwright color-warning cleanup | P3 | T164 | The real backend gate passes, but repeated `NO_COLOR` versus `FORCE_COLOR` warnings add noise to QA logs. |

## Superseded Or Completed

| Task | Status | Note |
| --- | --- | --- |
| T98 database-backed CSP telemetry store | Superseded by T106 | T106 delivered SQLite-backed dashboard telemetry. A centralized PostgreSQL variant is tracked as T109. |
| T106 database-backed dashboard telemetry | Completed | Implemented in current loop. |
| T105 admin RBAC for security dashboard | Completed | Implemented layered backend/JWT/operator-token dashboard access guard. |
| T99 CSP telemetry retention policy | Completed | Implemented shared age/count retention for in-memory and SQLite telemetry. |
| T102 Redis-backed CSP report limiter | Completed | Implemented Redis-backed fixed-window CSP report limiter with async route handling. |
| T111 backend global admin role contract | Completed | Added backend-owned `SECURITY_ADMIN` global role contract exposed through `/api/users/@me`. |
| T113 production guard configuration check | Completed | Dashboard guard now fails closed in production or when guard enforcement is required. |
| T118 global admin grant operations tool | Completed | Added `admin-cli` grant/revoke/list workflow for global roles. |
| T116 Redis-backed CSP limiter integration test | Completed | Added Docker-backed Redis limiter integration test gated by `NUXT_RUN_DOCKER_TESTS=true`. |
| T103 CSP rate-limit telemetry counter | Completed | Added sanitized in-memory limited-report counter and dashboard payload field. |
| T100 CSP telemetry alert threshold | Completed | Added aggregate CSP threshold evaluator and dashboard alert payload field. |
| T108 CSP alert threshold dashboard banner | Completed | Rendered active dashboard alert state and threshold reasons in `/security`. |
| T104 trusted proxy subject normalization review | Completed | CSP report rate-limit subjects now trust forwarded headers only from configured proxy peers. |
| T112 security dashboard operator token UX | Completed | Added in-dashboard operator token apply/clear flow with telemetry retry. |
| T114 CSP telemetry retention metrics | Completed | Added aggregate retention discard counters to stores, dashboard payload, and `/security`. |
| T117 Redis client lifecycle cleanup | Completed | Added optional limiter close contract and Nitro lifecycle cleanup for the default limiter. |
| T119 global admin audit log | Completed | Added in-memory and Postgres audit logging for global admin role grant/revoke commands. |
| T109 PostgreSQL/centralized CSP telemetry backend | Completed | Replaced app SQLite telemetry with centralized Postgres telemetry through `NUXT_CSP_TELEMETRY_POSTGRES_URL`. |
| T120 dashboard guard health endpoint | Completed | Added a secret-safe guard health endpoint and status model. |
| T121 admin CLI bootRun smoke test | Completed | Added a Docker-backed admin-cli Postgres bootRun smoke and fixed Spring constructor injection for the CLI runner. |
| T144 central runtime resource profiles | Completed | Aligned resource profiles and environment examples with Postgres, Flyway, Redis, and Kafka central Docker endpoints. |
| T145 Remove Runtime In-Memory Persistence Defaults | Completed | Production-like Spring profiles now require `postgres` and exclude runtime in-memory persistence beans. |
| T146 Kafka Gateway Event Bus Adapter | Completed | Added Kafka-backed Gateway event bus profile with sanitized envelope publishing and remote event delivery. |
| T147 Docker Compose Resource Topology Alignment | Completed | Docker Compose now mirrors central Postgres, Redis, and Kafka resource names and host ports. |
| T148 Central Redis Smoke Check | Completed | Added a real central Redis smoke for backend connectivity and Nuxt CSP Redis limiter coordination. |
| T151 Kafka Gateway Docker Smoke Test | Completed | Added a real central Kafka smoke proving node-a publish and node-b listener delivery through the broker. |
| T153 Compose Health Gate Script | Completed | Added a shared central Postgres, Redis, and Kafka readiness gate for local integration smokes. |
| T154 Central Redis Smoke CI Gate | Completed | Added a GitHub Actions `qa-central-redis` job and made the Redis smoke script cross-platform. |
| T155 Kafka Gateway Smoke CI Gate | Completed | Added a GitHub Actions `qa-central-kafka` job and made the Kafka smoke script cross-platform. |
| T156 Compose Health Failure Diagnostics | Completed | Added Docker, Compose, and platform-specific port diagnostics to central health failures. |
| T157 Central Redis CI Failure Artifacts | Completed | Added Redis CI failure artifact collection for Docker state, Redis logs, Gradle reports, and Vitest JUnit output. |
| T32 Dependency, SBOM & Vulnerability Gate | Completed | Added a CI-backed security gate with npm high/critical blocking, SBOM artifacts, backend dependency inventory, and allowlist expiry validation. |
| T158 Kafka Gateway CI Failure Artifacts | Completed | Added Kafka CI failure artifact collection for Docker state, Compose state/config, Kafka broker logs, and Gradle reports. |
| T159 Compose Health Diagnostic Failure Smoke | Completed | Added a controlled diagnostic failure smoke for central Compose health diagnostics. |
| T160 JVM Vulnerability Scanner And NPM SBOM Cleanup | Completed | Added OSV Maven/npm scanning, removed current high/critical findings through dependency upgrades, and added native package-lock SBOM evidence. |
| T124 distributed CSP rate-limit telemetry | Completed | Added Postgres-backed CSP rate-limit telemetry aggregation with async handler and dashboard support. |
| T150 Production Profile Guard Smoke Test | Completed | Added a CI-backed real `bootRun` smoke proving `production` without `postgres` fails with the explicit runtime profile guard message. |
| T123 CI Docker test toggle | Completed | Added a dedicated CI job that enables `NUXT_RUN_DOCKER_TESTS=true` for the Docker-backed web Redis limiter integration test. |
| T141 Admin CLI BootRun Smoke CI Gate | Completed | Added a dedicated CI job for the admin CLI bootRun smoke, with cross-platform Gradle selection, Compose Postgres startup, fresh DB migration warmup, and safer datasource env handling. |
| T125 CSP rate-limit dashboard UI | Completed | Added `/security` dashboard visibility for aggregate CSP rate-limited report totals, covering empty and non-zero states. |
| T126 CSP alert persistence | Completed | Added in-memory/Postgres CSP alert transition persistence and `/security` review history for active and cleared alert states. |
| T132 Global Admin Audit Review API | Completed | Added a guarded `SECURITY_ADMIN` backend endpoint and OpenAPI contract for reviewing global admin role audit entries. |
| T138 Dashboard Guard Health UI Panel | Completed | Added a secret-safe `/security` dashboard panel for guard health status, method categories, and warnings. |
| T139 Dashboard Guard Health Smoke Check | Completed | Added a CI-backed Nuxt production smoke that calls guard health and fails on fail-closed dashboard guard status. |
| T122 admin role runbook | Completed | Added a production runbook and contract for `SECURITY_ADMIN` grant, verify, audit review, and rollback. |
| T135 Explicit CSP Telemetry Postgres Migration | Completed | Added reviewed SQL migration and runbook for CSP telemetry, retention metrics, rate-limit telemetry, and alert transitions. |
| T136 CSP Telemetry Postgres Health Metric | Completed | Added dashboard payload/UI visibility for CSP telemetry storage backend readiness and write failures. |
| T137 CSP Telemetry SQLite Legacy Cleanup Note | Completed | Added legacy SQLite CSP telemetry archive/delete guidance and validation contract. |
| T161 Redis CLI Secret Handling In QA Health Checks | Completed | Replaced `redis-cli -a` in QA health paths with `REDISCLI_AUTH` and strengthened contracts. |
| T162 Strict Workspace Native NPM SBOM Cleanup | Completed | Fixed npm peer layout so strict workspace SBOM generation passes and removed security gate fallback execution. |
| T127 CSP alert acknowledgement workflow | Completed | Added fingerprint-bound acknowledgement, required reasons, bounded snooze, Postgres/in-memory storage, and `/security` UI workflow. |
| T128 CSP rate-limit subject diagnostics | Completed | Added secret-safe trusted-proxy subject diagnostics to the guarded dashboard payload and `/security` UI. |
| T129 ephemeral operator token flow | Completed | Added bootstrap exchange, 15-minute issued tokens, hash-only store, revoke flow, and `/security` expiry-only UI. |
| T169 Durable operator token store | Completed | Added Postgres-backed issued token hash and audit persistence with default central database selection. |
| T130 CSP retention metrics breakdown UI | Completed | Added `/security` age-pruned and max-entry-pruned retention discard counters under the aggregate total. |
| T131 Redis CSP limiter lifecycle metrics | Completed | Added secret-safe Redis CSP limiter lifecycle counters and `/security` visibility. |
| T133 Global Admin Audit Retention And Export Policy | Completed | Added 365-day audit review retention cutoff, JSON export policy metadata, and runbook guidance. |
| T134 Duplicate-Safe Grant Audit Result | Completed | Duplicate grant commands now record `GRANT` audit entries with `NOOP` instead of `APPLIED`. |
| T140 Backend Auth Check Probe | Completed | Added secret-safe backend auth reachability probe to dashboard guard health and `/security`. |
| T142 Admin CLI Smoke Database Isolation | Completed | Default admin CLI smoke now uses generated cleanup-safe fixtures and passes under both Windows PowerShell and PowerShell Core. |
| T143 Admin CLI Grant/Revoke BootRun Smoke | Completed | Admin CLI smoke now proves grant/revoke mutation, role state, and audit rows through real Gradle bootRun. |
| T152 Kafka Gateway Consumer Failure Policy | Completed | Kafka consumer malformed, invalid envelope, and listener failures now publish secret-safe dead-letter records instead of silently dropping or throwing. |
| T177 Kafka Gateway DLQ retention, alert, and replay workflow | Completed | Added contract-checked DLQ runbook, 168-hour retention/default alert threshold policy, and CI coverage for the runbook contract. |
| T178 Kafka Gateway DLQ metrics and alert integration | Completed | Added reason-tagged Micrometer DLQ counters and secret-safe threshold alert snapshots. |
| T107 CSP telemetry trend chart | Completed | Added a secret-safe six-hour CSP trend payload and compact `/security` chart. |
| T110 Node SQLite runtime compatibility gate | Completed | Contract now prevents legacy SQLite runtime config/import reintroduction and documents the Node 24 `node:sqlite` compatibility boundary. |
| T115 SQLite telemetry maintenance command | Superseded by T110/T137 | SQLite CSP telemetry is legacy-only; archive/delete guidance is documented without adding a maintenance command that would reintroduce SQLite dependency surface. |
| T165 Real LiveKit Media Smoke | Completed | Added environment-gated real media smoke and locally verified two browser participants exchanging synthetic LiveKit video tracks. |
| T60 shared Gateway session registry and cross-node RESUME | Completed | Added shared session registry port, Redis-backed registry, secret-safe session metadata serialization, and deterministic cross-node resume coverage. |
| T59 Redis Streams consumer-group hardening | Completed | Redis Gateway fanout now uses consumer groups, pending recovery reads, ACKs, stream trim retention, and aggregate processing metrics. |
| T61 Redis multi-node Gateway fanout smoke | Completed | Added a real central Redis Gateway fanout smoke and corrected Redis consumer groups to node-scoped broadcast groups. |
| T63 Redis Gateway DLQ policy | Completed | Malformed Redis records and listener failures now write secret-safe DLQ metadata, metrics, alert state, and a runbook contract. |
| T182 Gateway session registry TTL | Completed | Redis session registry now uses per-session TTL keys, prunes stale index entries, and has real central Redis smoke coverage. |
| T62 Gateway subscription reconciliation | Completed | Resume/poll now re-register visible subscriptions, with focused tests, Redis smoke coverage, and a screenshot report. |
| T183 Redis Gateway source-node duplicate suppression policy | Completed | Same-node Redis records are decoded and ACKed without duplicate local listener delivery when `sourceNodeId` matches the current node. |
| T164 Real Backend Browser Smoke Default Gate | Completed | Added `npm run e2e:real-backend`, local Compose bootstrap, 18080 backend default, CI contract wiring, and owned Java child cleanup for the real backend Playwright smoke. |
| T166 Discord Shell Layout Compression Pass | Completed | Added Playwright layout guard, compressed dense desktop shell panels, fixed skip-link spacing, and wrapped admin/audit text without viewport overflow. |

## Recommended Next Task

T55 should run next. The next highest-priority gap is restore snapshot hash comparison for backup confidence.
