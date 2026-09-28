# T43 멘션 알림함과 브라우저 알림 설계

## 목표와 범위

메시지 발행, PostgreSQL 알림 저장소, 웹 Inbox를 연결한다. 직접 사용자 멘션과 `@everyone`을 알림함에 기록하고, 사용자가 명시적으로 허용한 브라우저에서 앱 탭이 백그라운드에 있을 때 시스템 알림을 보낸다. 채널의 최신 메시지가 화면에 보이면 그 지점까지 읽음 처리하며, 읽음 위치는 재접속 후에도 유지한다.

이번 범위에는 `@here`, 역할 멘션, DM·서버 이벤트의 신규 알림 생성, 닫힌 탭에 보내는 Web Push, 모바일 네이티브 푸시를 넣지 않는다. 기존 Inbox의 DM 목록은 현재 동작을 유지한다. 새 마이크로서비스는 만들지 않는다.

## 확인한 기존 경계

- `PublishMessageUseCase`는 메시지와 `MessagePublished` outbox 이벤트를 저장한다. PostgreSQL·Kafka 경로의 `MessagePublishedRecord`는 메시지 본문과 멘션을 싣지 않고 `messageId`를 전달한다.
- `JdbcNotificationInboxService`와 `notification_items`는 사용자·메시지·종류별 중복 방지와 채널 sequence까지 읽음 처리를 이미 제공한다. 발행 이벤트에서 호출하는 연결과 사용자 REST API는 없다.
- `apps/web/pages/app.vue`의 Inbox는 `apps/web/stores/shell.ts`의 로컬 메시지에서 멘션을 계산한다. 실제 백엔드 메시지 전송은 아직 구조화된 `mentions`를 보내지 않는다.
- 웹 채널 읽음 표시는 로컬 상태이며 백엔드 Presence 읽음 위치는 메모리다. 재접속 후 일관성을 위해 영속 읽음 위치가 필요하다.

## 선택한 방식

메시지 발행 이벤트를 알림 투영의 입력으로 사용한다. 알림 투영은 `messageId`로 저장된 메시지와 해당 채널의 DB `sequence`를 조회하고 수신자별 `notification_items`에 기록한다. `!kafka` 로컬 dispatcher와 Kafka 알림 consumer는 같은 투영 로직을 사용한다. Kafka에서는 Gateway consumer와 별도 consumer group으로 처리해 서로의 전달 성공 여부에 종속되지 않는다. 재전달은 `(user_id, source_id, kind)` 고유 제약과 한 이벤트의 트랜잭션 처리로 멱등화한다. 읽음 위치가 먼저 기록된 뒤 이벤트가 도착하면 그 위치 이하의 알림은 처음부터 읽음으로 저장한다.

메시지 저장 직후 동기적으로 알림을 기록하는 방식은 메시지 발행과 알림 장애를 결합하므로 선택하지 않는다. 웹에서만 알림을 계산하는 방식은 새로고침·다른 기기의 상태가 달라지므로 선택하지 않는다.

## 멘션과 수신자 규칙

1. 웹 작성창은 실제 사용자 ID로 해석된 직접 멘션을 `USER`, 독립된 예약어 `@everyone`을 `EVERYONE` 구조화 멘션으로 메시지 API에 보낸다. 자유 텍스트 `@이름`이 특정 사용자 ID로 해석되지 않으면 그 텍스트만으로 알림을 만들지 않는다. 서버는 메시지 본문을 다시 파싱하지 않는다.
2. 채널에 글을 쓸 수 있는 사용자라면 별도 `@everyone` 발신 제한을 두지 않는다. `@here`, `ROLE`, `CHANNEL`은 메시지 모델에 존재하더라도 이번 알림 투영 대상에서 제외한다.
3. 수신자는 이벤트 처리 시점에 해당 채널을 볼 수 있는 구성원으로 한정한다. 작성자는 제외한다. 직접 멘션과 `@everyone`에 동시에 해당해도 사용자당 메시지 한 건만 기록한다.
4. 조회 시점에도 채널 열람 권한을 확인한다. 권한을 잃은 채널의 알림 내용·개수·브라우저 알림은 노출하지 않는다. 권한 확인이나 메시지 조회가 실패하면 알림을 공개하지 않고 재처리 가능한 실패로 남긴다.
5. 알림에는 메시지 ID, 채널 ID, DB sequence, 작성자와 채널을 식별할 최소 요약만 저장한다. 브라우저 알림과 로그에 메시지 본문을 넣지 않는다.

## 읽음 상태와 API

인증된 본인만 알림 목록·읽지 않은 개수·채널 읽음 위치를 조회한다. 알림 목록은 최신순으로 페이지 크기와 cursor를 제한하며, 권한이 사라진 채널의 항목은 서버에서 제외한다. 웹 Inbox의 멘션 영역은 이 API를 사용하고, 채널 읽지 않음은 영속 읽음 위치와 저장된 메시지 sequence를 기준으로 계산한다. 자기 메시지는 읽지 않은 개수에 포함하지 않는다.

기존 `PUT /api/presence/channels/{channelId}/read` 계약을 확장해 인증·채널 열람 권한을 확인한 다음, 클라이언트가 요청한 sequence를 해당 채널의 실제 저장 메시지 범위로 검증한다. 읽음 위치는 사용자·채널별로 PostgreSQL에 단조 증가하도록 저장한다. 같은 처리에서 해당 sequence 이하의 `notification_items`를 읽음으로 바꾼다. 읽음 응답과 이후 목록 조회는 같은 저장 상태를 반영한다. 웹은 채널을 열고 최신 메시지가 화면에 보인 뒤 이 API를 호출하며, 성공한 sequence까지만 로컬 배지를 갱신한다. 실패하면 읽음으로 보이게 하지 않고 재시도를 허용한다.

## 브라우저 시스템 알림

설정의 ‘브라우저 알림 켜기’를 클릭했을 때만 `Notification.requestPermission()`을 호출한다. 브라우저별 활성화 선택은 해당 브라우저에 저장하고, 브라우저 권한이 `granted`일 때만 활성 상태로 표시한다. 권한 거부·미지원 상태를 설정 화면에 표시하며 알림함 자체는 계속 쓸 수 있다.

로그인한 앱 탭이 열려 있고 `document.visibilityState === 'hidden'`일 때, 수신자에게 전달된 직접 멘션 또는 `@everyone` 이벤트에만 시스템 알림을 띄운다. 현재 열람 권한과 읽지 않은 알림 여부를 서버 결과로 확인한 뒤 표시한다. 제목에는 작성자와 채널만 사용하고 본문은 노출하지 않는다. 같은 메시지 ID를 `Notification`의 `tag`로 사용해 중복 표시를 줄인다. 알림 클릭 시 앱의 해당 채널을 연다. 탭이 닫혔을 때의 전달은 별도 Web Push 작업이다.

## 검증 기준

- 직접 멘션, `@everyone`, 중복 멘션, 작성자 제외, 숨긴 채널, 권한 회수, 이벤트 재전달을 백엔드 테스트로 검증한다.
- 읽음 위치가 감소하지 않고 새로고침·재로그인·서버 재시작 후 유지되며, 읽음 API와 멘션 개수가 일치하는지 PostgreSQL 통합 테스트로 확인한다.
- 실제 메시지 전송 요청의 구조화 멘션, Inbox API 표시·이동, 최신 메시지 가시성 후 읽음 처리, 권한 거부·미지원·백그라운드 브라우저 알림을 웹 테스트로 확인한다.
- 신규 REST 계약을 OpenAPI에 반영하고 `npm run openapi:check`, 관련 백엔드 테스트, 웹 테스트와 빌드를 실행한다.

## 기존 자료

- `backend/modules/message/src/main/java/com/example/discord/message/PublishMessageUseCase.java`
- `backend/modules/message/src/main/java/com/example/discord/message/MessagePublishedRecord.java`
- `backend/boot/src/main/java/com/example/discord/notification/JdbcNotificationInboxService.java`
- `backend/boot/src/main/resources/db/migration/V12__notification_inbox.sql`
- `apps/web/pages/app.vue`, `apps/web/stores/shell.ts`
- Wiki: `index.md`, `wiki/Backend Architecture.md`, `wiki/Frontend Client Architecture.md`, `wiki/Current Roadmap And Risks.md` (앞선 범위 검토)
