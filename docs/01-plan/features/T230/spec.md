# T230 Thread 파일럿 명세

상태는 초안이다. [T230 계획](../T230-declarative-transactions.plan.md)의 1단계 트랜잭션 기반을 전제로, 2단계 파일럿의 동작과 검증 조건을 정의한다. 도메인 용어와 범위는 [Community 컨텍스트](../../../contexts/community.md)를 따른다.

## 사용자 동작

포럼 채널에 메시지를 보낼 권한이 있는 사용자가 해당 포럼에 허용된 태그를 하나 이상 선택해 ForumPost를 만든다. 공개 HTTP 진입점은 `POST /api/channels/{forumChannelId}/forum-posts`, Java 진입점은 `ThreadService.createForumPost`다. `ThreadController`가 인증 사용자와 포럼을 확인하고 명령을 전달한다.

## 수용 조건

| 상황 | 기대 결과 |
| --- | --- |
| 유효한 요청 | 기존 응답 형식으로 HTTP 201을 반환하고 `threads`의 Thread와 `thread_post_tags`의 태그 연결을 함께 커밋한다. |
| 권한이 없거나 태그가 비어 있거나 다른 포럼의 태그를 보냄 | 기존 거절 계약을 유지하고 Thread와 태그 연결을 생성하지 않는다. |
| Thread 행을 삽입한 뒤 태그 연결 쓰기가 실패함 | 실제 PostgreSQL에서 새 Thread 행과 태그 연결이 모두 남지 않는다. |

## 검증과 후속 판단

- PostgreSQL 통합 테스트는 Spring이 주입한 `ThreadService` 빈을 호출한다. 첫 삽입 이후의 실패를 유도하고, 호출 종료 후 별도 연결에서 두 테이블을 조회한다.
- 기존 `PostgresThreadServiceTest`와 `ThreadControllerTest`의 성공·거절 계약을 재검증한다. PostgreSQL 게이트와 격리 DB 설정은 [T230 계획](../T230-declarative-transactions.plan.md)을 따른다.
- 파일럿 리뷰에 변경 파일·Gradle 프로젝트·테스트 범위를 기록한다. 이를 근거로 Boot 내부의 기능별 패키지 구조가 변경 범위를 줄이는지 평가한다. 파일럿 범위는 트랜잭션 전환이며 모듈 재배치와 ORM 도입은 후속 결정 사항이다.
