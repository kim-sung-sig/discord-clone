# T225 유비쿼터스 언어 용어집 재구성 (U0) Blueprint

## 승인 상태
- 승인됨.
  - 2026-10-30 사용자 요청: "시퀀스 그림과 이제 MSA 전환과 함께 유비쿼터스 기반으로 개발 진행해 보도록 합시다."
  - 이름 결정 4가지(Guild, User/GuildMember 구분, DirectMessageChannel/GroupDm, 역할별 행위자 이름)도 같은 날 사용자가 골랐다.
- 계획: `docs/01-plan/features/T225-msa-ubiquitous-language.plan.md`의 U0 단계

## 목표
- MSA 서비스 경계(T171-C0 ADR)에 맞춰 용어집을 context별로 나눈다.
- 같은 개념의 여러 이름 가운데 하나를 공식 이름으로 정한다.
- 이후 서비스 분리 작업(U2~U5)이 이 이름을 기준으로 진행되게 한다.

## 범위
- 루트 `CONTEXT-MAP.md`: context 목록, 관계, 공통 식별자, 전체에 걸친 이름 결정 요약
- `docs/contexts/{identity,community,message,gateway,presence,notification,media}.md`: context마다 다음을 담는다.
  - 용어 정의와 피할 이름(_Avoid_)
  - 관계
  - 예시 대화
  - 확인된 이름 충돌과 해결 방향(Flagged ambiguities)
- `docs/02-design/features/T225-ubiquitous-language.adr.md`: 이름 결정 ADR
- 기존 루트 `CONTEXT.md` 삭제. Message 용어는 `docs/contexts/message.md`로 그대로 옮긴다.
- `docs/01-plan/features/T225-msa-ubiquitous-language.plan.md`: 전체 계획

## 범위 밖
- 코드, DB, API 이름 변경. 각 context 분리 작업에서 한다.
- 저장 방식 결정. 검토 중인 T224 계획이 다룬다.
- 화면 문구 번역 변경

## 설계 결정
- 용어집 형식은 context별 `Language`(굵은 용어 + 1~2문장 정의 + `_Avoid_`), `Relationships`, `Example dialogue`, `Flagged ambiguities`다. 이 형식은 grill-with-docs의 CONTEXT-FORMAT 규칙을 따른다.
- 용어집이 여러 개이므로 진입점은 루트 `CONTEXT-MAP.md`로 한다. 다른 문서, 스크립트, 규칙에 `CONTEXT.md`를 참조하는 곳이 없는 것을 확인했다(rg 결과는 T225 계획 문서 1건뿐).
- 기존 Message 정의는 뜻을 바꾸지 않고 옮긴다. 다만 코드와 다른 점은 `_Avoid_`와 Flagged ambiguities에 사실로 적는다. 예: `ChannelMessageReader` 타입이 실제로는 없다는 점, DM이 별도 table을 쓴다는 점.
- 모든 정의와 "지금 코드는 이렇다"는 서술은 2026-10-30 조사 결과(하위 에이전트 5개)에 근거한다.

## Expected Changed Files
- `CONTEXT-MAP.md` (신규)
- `CONTEXT.md` (삭제)
- `docs/contexts/identity.md`, `community.md`, `message.md`, `gateway.md`, `presence.md`, `notification.md`, `media.md` (신규)
- `docs/02-design/features/T225-ubiquitous-language.adr.md` (신규)
- `docs/01-plan/features/T225-msa-ubiquitous-language.plan.md` (신규)
- `docs/03-analysis/task_T225-ubiquitous-language.blueprint.md` (이 문서)
- `docs/03-analysis/task_T225-merge-review.md` (증거 기록, 별도 commit)

## 검증 계획
- `git diff --check origin/main HEAD`
- 모든 `qa/*.contract.ps1`: main과 비교해 새 실패가 없어야 한다.
- 검토자 2명
  - 명세 준수: 사용자 결정 4가지가 반영됐는지, context가 ADR 서비스 경계와 맞는지 본다.
  - 정확성·품질: 용어집이 적은 "지금 코드 사실"이 실제 코드와 맞는지 본다. 파일·줄을 표본으로 확인한다.

## 위험
- 용어집의 코드 사실 서술이 틀리면, 이후 작업이 잘못된 전제로 진행된다. 정확성 검토로 확인한다.
- 이름만 정하고 코드가 늦게 따라오면, 한동안 문서와 코드가 다르다. 각 용어집의 Flagged ambiguities가 그 차이를 관리하는 목록 역할을 한다.
