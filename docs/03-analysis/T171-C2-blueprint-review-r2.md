# T171-C2 Blueprint 독립 재검수 R2

## 판정

- 결과: **승인**
- Plan Review: **91/100** (기준 85 통과)
- Security Review: **92/100** (기준 90 통과)
- P0/P1/P2: **P0 0 / P1 0 / P2 2**
- 검수 기준 revision: `a404197`
- working tree 상태: 대상 plan과 이전 review는 미추적 파일 상태였으며, 정적 검수만 수행했다.

## 기준 항목 확인

| 항목 | 판정 | 근거 |
|---|---|---|
| `authorization_watermark` 기반 stale deny | 통과 | `authorization_watermark` schema가 있고, watermark 부재 또는 row version < watermark면 deny한다고 명시한다. `docs/01-plan/features/T171-C2-rbac-projection.plan.md:72`, `:90`, `:97`, `:158` |
| per-service topic/audience/privacy | 통과 | topic이 `discord.authz.{service}.v1`로 service별이며 `audience` 필드와 audience별 topic filtering, 공용 fanout 금지를 명시한다. `docs/01-plan/features/T171-C2-rbac-projection.plan.md:44`, `:58`, `:67`, `:161` |
| DLQ masking/retention | 통과 | malformed/unknown event는 mutation 없이 metadata-only DLQ로 보내고 subject/resource truncated SHA-256 masking, raw event/token/body 미보관, 7일 retention을 명시한다. `docs/01-plan/features/T171-C2-rbac-projection.plan.md:69`, `:161` |
| `JdbcGuildSnapshotStore` 동일 transaction API | 통과 | 현재 코드는 `GuildSnapshotStore.save(Guild)`만 있고 `JdbcGuildSnapshotStore.save`가 자체 connection transaction을 연다. 보정 plan은 `save(guild, changes)` API와 source snapshot/version/outbox 동일 JDBC transaction commit으로 구현 경계를 지정한다. `backend/boot/src/main/java/com/example/discord/guild/GuildSnapshotStore.java:8`, `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java:68`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:104`, `:169`, `:170`, `:184` |
| service migration/build expected files | 통과 | 현재 message/websocket service build에는 permission/Kafka/JDBC projection 의존성이 없고 service migration도 없다. plan은 두 service `build.gradle.kts`와 service-owned migration 파일을 expected changed files에 포함한다. `backend/services/message/build.gradle.kts:12`, `backend/services/websocket/build.gradle.kts:12`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:172`, `:173` |
| tombstone replay | 부분 통과 | `AuthzProjectionRemoved`를 `permissionBits=0` + 증가 version으로 정의하고 duplicate/out-of-order/replay drill을 요구한다. 다만 tombstone 전용 replay assertion은 명시하면 더 좋다. `docs/01-plan/features/T171-C2-rbac-projection.plan.md:70`, `:97`, `:187` |

## P2

### P2 - tombstone replay 검증명이 일반 replay에 묻혀 있다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:70`, `docs/01-plan/features/T171-C2-rbac-projection.plan.md:187`
- 내용: zero-bit tombstone 자체의 replay/duplicate 동작은 설계상 가능하지만, acceptance가 일반 replay drill로만 적혀 있어 구현자가 삭제형 tombstone 케이스를 빠뜨릴 수 있다.
- 제안: consumer test 항목에 `removed event duplicate/replay keeps permissionBits=0 and does not resurrect old allow`를 한 줄 추가한다.

### P2 - 리뷰 evidence 파일명이 현재 루프 산출물과 다르다

- 위치: `docs/01-plan/features/T171-C2-rbac-projection.plan.md:178`
- 내용: expected changed files는 `T171-C2-rbac-projection-review.md`를 요구하지만 이번 재검수 산출물은 `T171-C2-blueprint-review-r2.md`다. 구현 차단은 아니지만 evidence 추적명이 갈라질 수 있다.
- 제안: 최종 구현 blueprint에서 실제 review artifact명을 하나로 고정한다.

## 승인 사유

이전 P1이던 stale fail-closed source, audience/privacy, 동일 transaction API, service build/migration scope가 모두 구현 지시 수준으로 보정됐다. P2 두 건은 검증 명시성과 산출물 명명 문제이며, 기준인 plan >=85, security >=90, P0/P1 0을 깨지 않는다.

## 검수 범위

- `docs/01-plan/features/T171-C2-rbac-projection.plan.md`
- `docs/03-analysis/T171-C2-blueprint-review.md`
- `backend/boot/src/main/java/com/example/discord/guild/GuildSnapshotStore.java`
- `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildSnapshotStore.java`
- `backend/boot/src/main/java/com/example/discord/guild/PersistentGuildService.java`
- `backend/services/message/build.gradle.kts`
- `backend/services/websocket/build.gradle.kts`
- `backend/services/community/build.gradle.kts`
- `backend/modules/permission/src/main/java/com/example/discord/permission/*`
- Wiki used: `index.md`, `wiki/Backend Architecture.md`
