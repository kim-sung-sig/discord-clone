# T43 멘션·DM·서버 알림함과 브라우저 알림 설계

## 목표와 범위

메시지 발행, DM 전송, 서버 일정 변경을 PostgreSQL 알림 저장소와 웹 Inbox에 연결한다. 직접 사용자 멘션, `@everyone`, 새 DM, 서버 일정 생성·취소를 알림함에 기록한다. 사용자가 명시적으로 허용한 브라우저에서 앱 탭이 백그라운드에 있을 때 직접 멘션, `@everyone`, 새 DM, 서버 일정 생성·취소에 시스템 알림을 보낸다. 채널과 DM의 읽음 위치는 재접속 후에도 유지한다.

이번 범위에는 `@here`, 역할 멘션, 다른 사람의 RSVP 변경 알림, 닫힌 탭에 보내는 Web Push, 모바일 네이티브 푸시를 넣지 않는다. 새 마이크로서비스는 만들지 않는다.

## 확인한 기존 경계

- `PublishMessageUseCase`는 메시지와 `MessagePublished` outbox 이벤트를 저장한다. PostgreSQL·Kafka 경로의 `MessagePublishedRecord`는 메시지 본문과 멘션을 싣지 않고 `messageId`를 전달한다.
- `JdbcNotificationInboxService`와 `notification_items`는 사용자·메시지·종류별 중복 방지와 채널 sequence까지 읽음 처리를 이미 제공한다. 발행 이벤트에서 호출하는 연결과 사용자 REST API는 없다.
- `apps/web/pages/app.vue`의 Inbox는 `apps/web/stores/shell.ts`의 로컬 메시지에서 멘션을 계산한다. 실제 백엔드 메시지 전송은 아직 구조화된 `mentions`를 보내지 않는다.
- 웹 채널 읽음 표시는 로컬 상태이며 백엔드 Presence 읽음 위치는 메모리다. 재접속 후 일관성을 위해 영속 읽음 위치가 필요하다.
- `InMemorySocialService.sendDirectMessage`는 DM 영수증만 반환하고 본문/읽음 위치를 저장하지 않는다. 알림 클릭 후 대화를 다시 볼 수 있으려면 DM 저장·조회가 필요하다.
- `JdbcServerEventService`에는 일정과 `EVENT_CREATED`·`EVENT_CANCELED` 신호가 저장되지만 사용자 REST API와 알림 연결은 없다.

## 선택한 방식

메시지 발행 이벤트를 알림 투영의 입력으로 사용한다. 알림 투영은 `messageId`로 저장된 메시지와 해당 채널의 DB `sequence`를 조회하고 수신자별 `notification_items`에 기록한다. `!kafka` 로컬 dispatcher와 Kafka 알림 consumer는 같은 투영 로직을 사용한다. Kafka에서는 Gateway consumer와 별도 consumer group으로 처리해 서로의 전달 성공 여부에 종속되지 않는다. 재전달은 `(user_id, source_id, kind)` 고유 제약과 한 이벤트의 트랜잭션 처리로 멱등화한다. 읽음 위치가 먼저 기록된 뒤 이벤트가 도착하면 그 위치 이하의 알림은 처음부터 읽음으로 저장한다.

메시지 저장 직후 동기적으로 알림을 기록하는 방식은 메시지 발행과 알림 장애를 결합하므로 선택하지 않는다. 웹에서만 알림을 계산하는 방식은 새로고침·다른 기기의 상태가 달라지므로 선택하지 않는다.

DM과 서버 일정에는 현재 메시지와 같은 outbox가 없다. 두 경로는 각 원본 저장과 알림 기록을 하나의 PostgreSQL transaction으로 묶는다. 별도 이벤트 버스·서비스를 추가하지 않는다. 요청 실패 시 원본과 알림 모두 rollback한다.

## 멘션과 수신자 규칙

1. 웹 작성창은 실제 사용자 ID로 해석된 직접 멘션을 `USER`, 독립된 예약어 `@everyone`을 `EVERYONE` 구조화 멘션으로 메시지 API에 보낸다. 자유 텍스트 `@이름`이 특정 사용자 ID로 해석되지 않으면 그 텍스트만으로 알림을 만들지 않는다. 서버는 메시지 본문을 다시 파싱하지 않는다.
2. 채널에 글을 쓸 수 있는 사용자라면 별도 `@everyone` 발신 제한을 두지 않는다. `@here`, `ROLE`, `CHANNEL`은 메시지 모델에 존재하더라도 이번 알림 투영 대상에서 제외한다.
3. 수신자는 이벤트 처리 시점에 해당 채널을 볼 수 있는 구성원으로 한정한다. 작성자는 제외한다. 직접 멘션과 `@everyone`에 동시에 해당해도 사용자당 메시지 한 건만 기록한다.
4. 조회 시점에도 채널 열람 권한을 확인한다. 권한을 잃은 채널의 알림 내용·개수·브라우저 알림은 노출하지 않는다. 권한 확인이나 메시지 조회가 실패하면 알림을 공개하지 않고 재처리 가능한 실패로 남긴다.
5. 알림에는 메시지 ID, 채널 ID, DB sequence, 작성자와 채널을 식별할 최소 요약만 저장한다. 브라우저 알림과 로그에 메시지 본문을 넣지 않는다.

## DM·서버 알림 규칙

1. DM은 기존 친구·차단 정책을 통과한 1:1 전송만 대상으로 한다. 영수증과 본문을 PostgreSQL에 저장하고 대화별 단조 sequence를 부여한다. 대상 사용자 한 명에게 `DM` 알림을 기록하며 작성자는 제외한다. 대화 조회·읽음 API는 두 참여자에게만 허용한다. 그룹 DM은 이번 연결에서 제외한다.
2. 서버 알림은 기존 서버 일정의 `EVENT_CREATED`와 `EVENT_CANCELED`만 대상으로 한다. 생성·취소 시점에 해당 일정 채널을 볼 수 있는 길드 구성원에게 `SERVER` 알림을 기록하고 행위자는 제외한다. RSVP 변경은 개인 작업이므로 다른 사용자에게 알리지 않는다. 같은 일정의 생성과 취소는 서로 다른 신호 ID를 원본으로 삼아 각각 한 건으로 기록한다.
3. 서버 일정 목록·생성·취소 API는 기존 `JdbcServerEventService`를 사용한다. 생성·취소는 기존 관리 권한, 조회는 해당 채널 열람 권한을 검사한다. 권한이 사라지면 해당 서버 알림과 내용은 조회 결과에서 제외한다.
4. DM·서버 알림도 기존 `NotificationPreferences`의 종류별 선택을 적용한다. 알림 클릭 시 DM은 대화로, 서버 일정은 해당 채널/일정으로 이동한다.

## 읽음 상태와 API

인증된 본인만 알림 목록·종류별 읽지 않은 개수·채널/DM 읽음 위치를 조회한다. 알림 목록은 최신순으로 페이지 크기와 cursor를 제한하며, 권한이 사라진 채널의 항목은 서버에서 제외한다. 웹 Inbox는 `MENTION`·`DM`·`SERVER`를 이 API에서 표시한다. 채널 읽지 않음은 영속 읽음 위치와 저장된 메시지 sequence를 기준으로 계산한다. 자기 메시지는 읽지 않은 개수에 포함하지 않는다.

기존 `PUT /api/presence/channels/{channelId}/read` 계약을 확장해 인증·채널 열람 권한을 확인한 다음, 클라이언트가 요청한 sequence를 해당 채널의 실제 저장 메시지 범위로 검증한다. 읽음 위치는 사용자·채널별로 PostgreSQL에 단조 증가하도록 저장한다. 같은 처리에서 해당 sequence 이하의 `notification_items`를 읽음으로 바꾼다. 읽음 응답과 이후 목록 조회는 같은 저장 상태를 반영한다. 웹은 채널을 열고 최신 메시지가 화면에 보인 뒤 이 API를 호출하며, 성공한 sequence까지만 로컬 배지를 갱신한다. 실패하면 읽음으로 보이게 하지 않고 재시도를 허용한다.

DM 대화의 최신 메시지가 보이면 DM 읽음 API가 참여자와 실제 저장 sequence를 검증하고 대화별 읽음 위치와 `DM` 알림을 함께 갱신한다. 서버 일정 알림은 해당 항목을 열 때 본인 소유·가시성을 확인한 뒤 개별 읽음 처리한다. 읽음 API 실패 시 어떤 종류의 배지도 먼저 지우지 않는다.

## 브라우저 시스템 알림

설정의 ‘브라우저 알림 켜기’를 클릭했을 때만 `Notification.requestPermission()`을 호출한다. 브라우저별 활성화 선택은 해당 브라우저에 저장하고, 브라우저 권한이 `granted`일 때만 활성 상태로 표시한다. 권한 거부·미지원 상태를 설정 화면에 표시하며 알림함 자체는 계속 쓸 수 있다.

로그인한 앱 탭이 열려 있고 `document.visibilityState === 'hidden'`일 때, 수신자에게 전달된 직접 멘션, `@everyone`, 새 DM, 서버 일정 생성·취소에 시스템 알림을 띄운다. 현재 열람 권한과 읽지 않은 알림 여부를 서버 결과로 확인한 뒤 표시한다. 제목에는 알림 종류와 출처만 사용하고 본문은 노출하지 않는다. 알림 ID를 `Notification`의 `tag`로 사용해 중복 표시를 줄인다. 알림 클릭 시 해당 채널·DM·서버 일정으로 이동한다. 탭이 닫혔을 때의 전달은 별도 Web Push 작업이다.

## 검증 기준

- 직접 멘션, `@everyone`, 중복 멘션, 작성자 제외, 숨긴 채널, 권한 회수, 이벤트 재전달을 백엔드 테스트로 검증한다.
- DM 저장·재조회·참여자 제한·차단 정책·알림/읽음 일치, 일정 생성·취소의 가시 수신자·권한·중복 방지를 PostgreSQL 통합 테스트로 검증한다.
- 읽음 위치가 감소하지 않고 새로고침·재로그인·서버 재시작 후 유지되며, 읽음 API와 멘션 개수가 일치하는지 PostgreSQL 통합 테스트로 확인한다.
- 실제 메시지 전송 요청의 구조화 멘션, Inbox의 세 종류 표시·이동, 채널/DM 최신 메시지 가시성 후 읽음 처리, 권한 거부·미지원·백그라운드 멘션 알림을 웹 테스트로 확인한다.
- 신규 REST 계약을 OpenAPI에 반영하고 `npm run openapi:check`, 관련 백엔드 테스트, 웹 테스트와 빌드를 실행한다.

## 기존 자료

- `backend/modules/message/src/main/java/com/example/discord/message/PublishMessageUseCase.java`
- `backend/modules/message/src/main/java/com/example/discord/message/MessagePublishedRecord.java`
- `backend/boot/src/main/java/com/example/discord/notification/JdbcNotificationInboxService.java`
- `backend/boot/src/main/resources/db/migration/V12__notification_inbox.sql`
- `apps/web/pages/app.vue`, `apps/web/stores/shell.ts`
- Wiki: `index.md`, `wiki/Backend Architecture.md`, `wiki/Frontend Client Architecture.md`, `wiki/Current Roadmap And Risks.md` (앞선 범위 검토)
