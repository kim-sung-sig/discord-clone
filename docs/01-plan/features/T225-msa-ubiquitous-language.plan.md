# T225 유비쿼터스 언어 기반 MSA 전환 계획 (초안)

- 상태: **U0 진행 중** (2026-10-30). 사용자가 "MSA 전환과 함께 유비쿼터스 기반으로 개발을 진행하자"고 요청해서 작성했다. 핵심 이름 4가지는 같은 날 사용자가 결정했다(4절).
- 기준 문서
  - MSA 목표 구조 ADR: `docs/02-design/features/T171-C0-msa-target-state.adr.md`. 상태는 Draft이고, 첫 설계 웨이브는 2026-08-09 승인됐다.
  - 저장 계층 계획 T224는 아직 저장소에 올리지 않은 초안이다. 처음에는 JPA 전환안으로 썼고, 이후 대화에서 MyBatis 쪽이 더 맞다는 의견이 나왔다. U1에서 방향을 확정해 다시 쓰고 올린다.
  - 2026-10-30 아키텍처 리뷰 보고서와 그림 2개(전체 구조, 메시지 발행 시퀀스)
- 원칙
  1. 이름을 먼저 정하고, 코드는 그 이름으로 옮긴다. 용어집에 없는 이름으로 새 모듈·테이블·API를 만들지 않는다.
  2. 서비스 하나는 자기 용어집(bounded context) 하나를 소유한다. 다른 서비스의 말은 경계에서 번역한다.
  3. 서비스를 하나 분리할 때마다 그 서비스 안의 리팩터링 후보(보고서)와 저장 방식 전환(T224)을 함께 처리한다. 같은 코드를 두 번 고치지 않기 위해서다.

## 1. 현재 위치

| 항목 | 상태 |
| --- | --- |
| ADR 서비스 경계 | Identity, Community, Message, Gateway Control, WebSocket Transport, Presence, Notification Worker, Media/Voice |
| 독립 실행 서비스 | identity, message, websocket, community(껍데기) |
| 완료된 전환 | 첫 설계 웨이브(C0, 2026-08-09 승인), RBAC 권한 projection(작업명 T171-C2), Gateway 제어(T171-C3), T171-C4 병합 기록 |
| 아직 boot에 있는 것 | Community 전체(guild, channel, role, invite, social, thread, moderation), Message 본체, Gateway, Presence, Notification |
| 용어집 | T225 이전에는 루트 `CONTEXT.md`에 Message 개념만 있었다. U0에서 `CONTEXT-MAP.md`와 `docs/contexts/*.md` 7개로 재구성했다. |
| 확인된 이름 충돌 | 서버를 guild·server·workspace·community로 부른다. 작성자는 author·sender·creator·owner·reporter·actor·requester로 부른다. 그 밖에 member와 user, DM·conversation·direct message, session(코드 네 곳에서 세 가지 뜻), sequence(5가지 뜻)가 섞여 있다. |
| 기능 공백 | 웹 화면이 `/ws/gateway`에 연결하지 않아서, 받는 사람은 새로고침해야 새 메시지를 본다. |

ADR의 단계 번호(C1~C6)와 실제 작업 이름(T171-C2 = RBAC projection 등)이 서로 다르다. 이 계획에서는 아래 단계 이름을 쓰고, ADR 번호는 괄호로 함께 적는다.

## 2. 단계

### U0. 용어 확정 (문서만, 작업 하나)
- [x] 용어집을 context별 파일로 재구성한다. 진입점은 `CONTEXT-MAP.md`이고, 내용은 `docs/contexts/{identity,community,message,gateway,presence,notification,media}.md`에 둔다.
- [x] 이름 충돌을 결정한다(4절). 결정한 이름, 버릴 이름, 그 이유를 용어집과 ADR에 남긴다.
- [x] context 사이 번역 표(context map)를 만든다. 예: Message의 `MessageAuthor(user)` = Identity의 `UserId`, Message의 `ChannelMessageTarget` = Community의 `ChannelId`.
- [x] 큰 결정은 ADR로 남긴다: `docs/02-design/features/T225-ubiquitous-language.adr.md`.
- [x] 용어집에 있지만 코드에 없는 용어(`ChannelMessageReader`)를 Flagged ambiguities에 적는다. 실제 정리는 U2·U4에서 한다.

### U1. 공통 기반 (작업 하나, 모든 서비스에 필요)
- [ ] 연결 풀을 넣는다(`PGSimpleDataSource` → Hikari, boot와 services 모두). 조회에 빠진 `LIMIT`도 추가한다.
- [ ] 복사된 어댑터를 shared 모듈로 모은다: 권한 projection store 3벌, projection Kafka 수신기 3벌, JWT 부트스트랩 4벌. 합치기 전에 계약 시험을 먼저 추가한다.
- [ ] Kafka 주제·그룹·시간 값을 설정 record로 모은다. 서비스마다 같은 값을 다시 쓰지 않게 하기 위해서다.
- [ ] 저장 방식을 결정하고 T224 계획을 확정해 올린다. 결정만 하는 단계이고, 첫 적용(파일럿)은 U3의 Invite에서 한다.

### U2. 실시간 연결 복구 (작업 하나, 사용자 체감 1순위)
- [ ] 웹 화면을 `/ws/gateway`에 연결한다. identify, heartbeat, resume, `MESSAGE_CREATE` 반영까지 포함한다.
- [ ] Gateway 소유 노드 id가 `"in-memory"`로 고정된 문제를 확인하고 고친다(여러 노드 펜싱).
- [ ] Gateway가 본문을 다시 읽을 때 가시성 규칙이 있는 읽기 경계(용어집의 `ChannelMessageReader`)를 쓰게 한다. 이미 있는 `ChannelMessageReadGuard`, `ChannelMessageQueryService`를 바탕으로 만든다.

### U3. Community context 분리 (ADR C5 logical split의 첫 서비스)
- [ ] 파일럿으로 Invite를 옮긴다. 용어 적용, `AcceptInviteUseCase`(트랜잭션 하나), 저장 방식 전환(T224 파일럿)을 함께 한다.
- [ ] Guild 권한 검사를 `GuildAccess` 하나로 모은다. 예외를 흐름 제어로 쓰지 않는다.
- [ ] guild, channel, permission, invite, social, thread, moderation, experience(Stage 포함), expression, bot, event(GuildEvent)를 Community 서비스로 옮긴다. 자체 DB, outbox, 권한 변경 이벤트를 갖춘다(T171-C0 §3.2, T225 ADR 결정 7).
- [ ] boot에서는 해당 controller를 Edge API 경유로 바꾸거나 제거한다. 지금 SocialController에 있는 in-memory/Jdbc null 분기도 없앤다.

### U4. Message context 정리 후 분리
- [ ] `Message.edit/delete/pin` 상태 전이와 `MessageTarget.guildId/channelId`를 넣는다. 유스케이스가 인자 10개짜리 생성자를 직접 부르지 않게 한다.
- [ ] DirectMessageChannel과 Thread 메시지를 `PublishMessageUseCase`로 통합한다. 이미 있는 `DirectMessageTarget`, `ThreadMessageTarget`을 실제로 쓰고, GroupDm 수신처를 추가한다. 자체 table로 우회하지 않는다.
- [ ] 메시지 조회 N+1을 없애고 cursor·index를 바로잡는다.
- [ ] message-service로 본체를 옮긴다. 지금 그쪽 outbox는 boot와 내용이 달라진 사본이므로, 원본 하나로 맞춘 뒤 옮긴다.

### U5. Presence·Notification·Media/Voice 분리
- [ ] `notification_items`를 직접 다루던 4곳(social, event, presence, notification)을 바꾼다. 다른 context는 이벤트를 내보내고, Notification Worker가 그것을 소비해 알림을 만들거나 읽음 처리한다(ADR §1.3).
- [ ] Presence는 TTL 상태와 이벤트 outbox를 가진 서비스로 분리한다.
- [ ] Media/Voice는 첨부 파일과 LiveKit 연동을 맡는다. 정의만 있고 검사하지 않던 음성 권한 `CONNECT`를 토큰 발급 때 검사한다.

### 단계마다 공통 완료 조건
- 새로 쓰거나 바꾼 이름은 모두 `CONTEXT-MAP.md`와 `docs/contexts/*.md`에 있다.
- 다른 서비스의 DB, 테이블, 내부 repository를 참조하지 않는다(ADR 결정 2).
- 조회 경로에 N+1이 없다. 검토 기준에 "조회 쿼리 수"를 추가하고, 필요하면 쿼리 수 시험을 둔다.
- 기존 Postgres 계약 시험, qa 계약 전체, 실제 백엔드 브라우저 시험이 통과한다.
- 작업 규칙(blueprint, 검토 2개 90점 이상, 병합 검토 기록)을 지킨다.

## 3. 순서 제안

U0(용어) → U1(공통 기반) → U2(실시간 연결) → U3(Community, Invite 파일럿부터) → U4(Message) → U5

- U2는 U0·U1과 독립적이라 병렬로 진행할 수 있다.
- U3의 Invite 파일럿이 T224 저장 방식 파일럿을 겸한다.

## 4. 용어 결정 (U0, 2026-10-30 결정)

1. 서버의 공식 이름: **`Guild`** (사용자 결정)
2. 사람: **계정은 `User`, Guild 소속은 `GuildMember`**. 인자 이름은 `userId` (사용자 결정)
3. 1:1·그룹 대화: **`DirectMessageChannel`·`GroupDm`** (사용자 결정)
4. 행위자: **역할별 이름(Author, Owner, Creator, Reporter, Actor)을 유지하고 정의를 고정한다** (사용자 결정)
5. Session과 Sequence: **`RefreshSession`·`GatewaySession`·`StageSession`, `ChannelSequence`·`UserEventSequence`·`ReadMarker`로 나눈다** (코디네이터 결정, 바꿀 수 있다)
6. 식별자: **`ChannelId`는 Guild 채널만 가리킨다. 메시지 순서와 읽음 위치는 MessageTarget 단위**로 한다 (코디네이터 결정)
7. 배치: **T171-C0 매핑을 따른다.** experience와 GuildEvent는 Community에 둔다 (코디네이터 결정)
8. 문서 구조: **`CONTEXT-MAP.md`와 `docs/contexts/*.md`** (코디네이터 결정)
