# Java 쓰기 구현 품질 스킬 설계

## 목적

`java-write-implementation`은 Java/Spring 쓰기 흐름을 작성·수정·리팩터링할 때 사용하는 구현 품질 스킬이다. 완성품의 최종 필수 검토인 `java-principal-review`와 역할이 다르다. 이 스킬은 구조 계약을 적용하고, 최종 리뷰는 별도로 실행한다.

## 활성화

- 명령: `/java-write-implementation [대상 또는 쓰기 흐름 설명]`
- 자연어: `Java 구현`, `쓰기 UseCase 구현`, `Command 구현`, `Orchestrator 구현`, `Activity 추가`
- 범위: 새로 만들거나 수정하는 Java/Spring 쓰기 UseCase. Read/Query 규칙은 포함하지 않는다.
- 기존 코드는 기능을 수정할 때만 점진적으로 전환한다.

## 구현 품질 계약

```text
Request
  → Resolver? → Orchestrator
      ├─ Dispatcher?
      ├─ Validator
      ├─ Factory
      ├─ Policy[]
      ├─ Activity[]
      └─ ModelPipeline?
```

- Orchestrator는 UseCase 흐름을 조정하고, 필요한 Validator·Factory·Policy·Activity를 조립·호출한다.
- Orchestrator가 여러 UseCase를 조합할 수 있고, 단순한 UseCase는 직접 조립할 수 있다.
- Dispatcher는 최상위 진입점으로 고정하지 않는다. Orchestrator 내부에서 결제 유형별 알림 준비처럼 조건에 따라 전략을 선택할 때 사용할 수 있다.
- `getType()` 기반 중계와 `supports(...)` 기반 선택은 모두 허용한다. 미등록 Command를 Dispatcher로 라우팅하면 실패한다. 중복 전략의 단일·다중 실행은 사용하는 중계 방식이 결정한다.
- 전략 분기는 나중에 이벤트 기반 처리로 분리할 수 있게 Orchestrator의 나머지 흐름과 결합하지 않는다.

## 모델과 구성 요소 책임

- Activity는 하나의 Aggregate Root에 대한 상태·규칙·도메인 작업만 책임진다. 보통 자신이 맡은 Aggregate Root의 저장까지 담당한다.
- Factory는 객체·도메인 모델 생성에만 사용한다. 조건 분기 위임에는 사용하지 않는다.
- Validator는 null, 형식, 길이, 범위, 파싱 가능 여부 같은 입력 유효성을 검증한다. 실패는 입력 오류다.
- Policy는 유효한 모델과 실행 컨텍스트의 권한, 상태 전이, 한도, 업무 규칙을 검증한다. 실패는 업무상 거절이다.
- Validator와 Policy는 모델을 변경하지 않는다. 모델 변경은 Factory, Activity, 필요한 ModelStage만 수행한다.
- Factory 이후 Policy, Activity, ModelPipeline은 요청 DTO가 아닌 도메인 모델을 받는다.

## Request, Command, Resolver

- Command는 Request를 해석·변경해 Activity 메서드 파라미터로 전달할 의미 있는 값의 묶음이다.
- Command 객체는 필수가 아니다. Target·Collection 링크처럼 객체가 의미를 더하지 않는 값은 명시적 타입 파라미터로 직접 전달한다.
- Resolver는 Request를 Command 또는 직접 파라미터로 바꾸기 전 조회·해석이 필요할 때만 둔다.
- Resolver가 Aggregate Root를 전달할지, 식별자·해석된 컨텍스트만 전달하고 Activity가 조회할지는 맥락에 따라 정한다. 이미 전달된 Aggregate Root는 같은 흐름에서 다시 조회하지 않는다.

## ModelPipeline과 이벤트

- 실제 도메인 이벤트 리스너와 구분하기 위해 `supports(model)` 기반 다중 처리는 `ModelPipeline`과 `ModelStage`로 부른다.
- 흐름 전체 완료 이벤트는 Orchestrator에, 특정 Aggregate Root의 도메인 사실은 Activity에 둘 수 있다.
- 각 쓰기 흐름은 트랜잭션 소유자를 Orchestrator 또는 Activity 중 하나로 명시한다. 여러 Aggregate Root 또는 외부 호출을 같은 트랜잭션에 묶지 않으면 Outbox 또는 이벤트 분리 여부를 명시한다.

## 메서드 시그니처

- 메서드 파라미터는 3개 이하를 기본으로 한다.
- 4개 이상이면 도메인상 이유를 남기거나 의미 있는 값 객체·파라미터 객체·모델로 묶는 방안을 검토한다.
- 개수만 줄이려는 의미 없는 Request·Command 객체 생성은 금지한다.

## 스킬 실행 흐름과 출력

1. 대상이 쓰기 흐름인지 확인하고, 기존 Orchestrator·Activity·Aggregate Root·테스트를 읽는다.
2. Resolver, Dispatcher, Command 객체, 저장 위치, 트랜잭션 소유자의 선택 여부와 근거를 정한다.
3. 구조 계약에 맞게 최소 코드와 해당 모듈의 테스트를 작성·실행한다.
4. 다음 구현 품질 결과를 출력한다.

```text
# Java Write Implementation Check

대상:
쓰기 흐름:
Aggregate Root 소유 Activity:
선택한 구성: Resolver | Command | Dispatcher | ModelPipeline
선택 근거:
트랜잭션 소유자·경계:
파라미터 4개 이상 예외: 없음 | 근거
구조 계약 충족 여부:
실행한 테스트:
잔여 위험:
```

## Hookify 연계

프로젝트 루트 `.codex/`에 다음 로컬 Hook 규칙을 둔다.

1. `.codex/hookify.warn-java-write-contract.local.md`
   - `event: file`, `action: warn`
   - `backend/**/src/main/java/**/*.java` 편집 시 활성화한다.
   - 쓰기 흐름을 수정하는 경우 Orchestrator 조립, Activity의 단일 Aggregate Root 책임, Validator/Policy 비변경성, 3개 이하 파라미터 원칙을 확인하도록 안내한다.

2. `.codex/hookify.require-java-write-quality-before-stop.local.md`
   - `event: stop`, `action: warn`
   - 작업 종료 전, 이번 작업이 Java 쓰기 변경이면 `/java-write-implementation`을 실행하도록 안내한다.
   - 최종 산출물 검토는 별도로 `/java-principal-review`를 실행하도록 안내한다.

Hookify는 스킬을 직접 실행하거나 실행 이력을 검증할 수 없으므로, 두 규칙은 경고만 한다. `block`은 정상 작업까지 막을 위험이 있어 사용하지 않는다.

## 비범위

- Read/Query 구현 규칙
- Markdown/HTML 검토 보고서 파일 저장
- JPA, Kafka, Redis, 분산 시스템의 최종 품질 리뷰. 이는 `java-principal-review`의 인프라 규칙팩이 담당한다.
