# T225 병합 검토 기록

## 대상

- 작업 브랜치: `task_T225-ubiquitous-language`
- 기준 `origin/main` SHA: `d5bff49e2d3f4882e6721562692b42e652c23b2d`
- 검토한 내용 SHA: `923e40a`

  | commit | 내용 |
  | --- | --- |
  | `b2da7dd` | 용어집을 context 7개로 나누고 이름 결정 ADR·계획·설계 문서를 추가했다. 루트 `CONTEXT.md`는 지우고 내용을 `docs/contexts/message.md`로 옮겼다. |
  | `23d3cbd` | 1차 검토 후속: 코드 사실 오류를 바로잡았고, T171-C0 매핑(StageSession은 Community)에 맞췄다. 식별자 범위를 나눴고, 알림을 이벤트 소비로 바꿨다. |
  | `3f24166` | 2차 검토 P3 후속: 순서 번호와 읽음 위치를 MessageTarget 단위로 정했다. 출처를 명시했고, 코드에 없는 타입을 표시했고, 계획을 갱신했다. |
  | `923e40a` | 남은 ADR 절 참조를 `T171-C0 §`로 명시했다. |

- 증거 기록: 이 문서만 바꾸는 commit이며, 검토한 내용 commit 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T225-ubiquitous-language.blueprint.md`

## 배경

2026-10-30 사용자 요청: "MSA 전환과 함께 유비쿼터스 기반으로 개발 진행해 보도록 합시다."

아키텍처 조사에서 같은 개념을 여러 이름으로 부르는 문제가 확인됐다. 예를 들어 서버는 guild·server·workspace로, 작성자는 author·sender·requester 등으로 불렸다. 반면 용어집에는 Message 개념만 있었다. MSA로 나누면 이름이 서비스 사이의 계약이 되므로, 서비스를 나누기 전에 이름부터 고정한다(계획 U0).

사용자가 정한 이름은 다음 4가지다.
- Guild
- User와 GuildMember 구분
- DirectMessageChannel과 GroupDm
- 역할별 행위자 이름

## 독립 검토

| 차수 | 구분 | 대상 SHA | 점수 | P0/P1/P2/P3 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 명세 준수 | `b2da7dd` | 84/100 | 0/0/6/6 | 불합격 |
| 1 | 정확성·품질 | `b2da7dd` | 74/100 | 0/2/9/4 | 불합격 |
| 2 | 명세·품질(수정분 재검토) | `23d3cbd` | 93 / 91 | 0/0/0/7 | 통과 |
| 3 | 명세 준수(수정분 재검토) | `923e40a` | 94/100 | 0/0/0/2 | **승인** |
| 3 | 정확성·품질(수정분 재검토) | `923e40a` | 92/100 | 0/0/0/2 | **승인** |

### 1차 정확성 검토 결과

용어집의 코드 사실 주장 25개를 실제 코드와 대조했다. 그 결과 6개는 틀렸고 2개는 일부만 틀렸다. 고친 항목은 다음과 같다.

- `FriendshipStatus`의 BLOCKED
- `PresenceStatus` 값
- Attachment 상태 2종
- GroupCallState
- Gateway 클라이언트 사용처
- 권한 계산 "3벌" 표현
- MessageAuthorDirectory 소유

P1 2건도 고쳤다. 하나는 StageSession 소유가 T171-C0 ADR과 달랐던 것이고, 다른 하나는 계획이 삭제된 파일을 참조하던 것이다. 2차와 3차 검토는 바뀐 사실을 HEAD 코드로 다시 확인했다.

## 검증 (`923e40a`, Windows, 코디네이터 직접 실행)

| 명령 | 결과 |
| --- | --- |
| `git diff --check origin/main HEAD` | 성공 |
| 모든 `qa/*.contract.ps1`(`b2da7dd` 기준) | 32개 통과. 실패 4건은 main과 같은 환경 관련 실패다. 이후 commit도 문서만 바꿨다. |
| `CONTEXT-MAP.md` 링크 | 7개 모두 유효(검토자 확인) |

## 설계 문서와의 일치

- 일치 여부: 일치
- 변경 파일이 설계 문서의 Expected Changed Files와 같다.

## 수용

- 설계 수용: 예
- 구현(문서) 수용: 예
- 검증 수용: 검토한 내용 기준으로 예

## 남은 위험·미룬 일

- P3, U4에서 정리
  - ADR 결정 6과 `message.md`는 "Thread가 자기 순서 번호를 가진다"고 적었다. 그러나 지금 코드는 Thread 메시지를 부모 채널 번호로 함께 매긴다(`InMemoryMessageService`의 `ChannelKey(guildId, channelId)`). U4에서 목표 규칙으로 할지, 부모 번호를 공유한다고 적을지 정한다.
  - 계획 60행에 "(ADR §1.3)"이 남아 있다. "T171-C0 §1.3"으로 고친다.
- 코드 이름 변경은 이 작업 범위 밖이다. 각 용어집의 Flagged ambiguities가 바꿀 목록이다.
  - 예: `ServerEvent` → `GuildEvent`, `requesterId` → `actorId`(controller 14곳), `GroupDmChannel` → `GroupDm`, `VoiceRoomToken` → `VoiceJoinToken`
- 코디네이터가 정한 항목 중 사용자가 따로 확인하지 않은 것이 있다. 바꾸려면 T225 ADR을 개정한다.
  - Session·Sequence 분리
  - ChannelId 범위
  - GuildEvent 배치 해석
  - FriendRequest의 Requester·Addressee 예외
