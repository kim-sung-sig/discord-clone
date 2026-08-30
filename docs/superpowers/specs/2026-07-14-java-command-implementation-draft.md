# Java 쓰기 구현 품질 계약 — 초안

> 상태: 협의 중. 쓰기 흐름을 다루며 Read 규칙은 이후 별도로 추가한다.

## 목적

이 계약은 구현 중인 Java/Spring 쓰기 코드의 품질과 구조를 통일한다. 완성품의 최종 필수 검토인 `java-principal-review`와는 별개다.

## 적용 범위

- 새로 작성하거나 수정하는 쓰기 UseCase에만 적용한다.
- 기존 코드는 해당 기능을 수정할 때 점진적으로 전환한다.
- 단일 구조를 모든 기능에 강제하지 않는다. 선택 가능한 조립 방식을 제공한다.

## 메서드 파라미터

- 메서드 시그니처는 파라미터 3개 이하를 기본으로 한다.
- 4개 이상이 필요한 경우에는 도메인상 필요한 이유를 남기거나, 의미 있는 값 객체·파라미터 객체·모델로 묶는 방안을 검토한다.
- 단순히 개수를 맞추기 위해 의미 없는 요청 객체나 Command 객체를 만들지 않는다.

## 기본 구조

```text
Request
  → Resolver (필요할 때만)
  → Orchestrator (UseCase 흐름 담당)
      → Dispatcher (필요할 때만, 내부 전략 분기)
      → Activity[] (도메인 서비스)
          → Aggregate Root 하나의 모델 책임
```

- Orchestrator는 요청을 해석한 값과 UseCase 흐름에 맞춰 Activity, Policy, Validator, Factory를 조립하고 호출한다.
- Dispatcher는 최상위 진입점으로 고정하지 않는다. Orchestrator 내부에서 결제 유형별 알림 준비처럼 조건에 따라 전략을 선택할 때 사용할 수 있다.
- Activity는 하나의 Aggregate Root에 대한 상태·규칙·도메인 작업만 책임진다.
- Activity는 보통 자신이 맡은 Aggregate Root의 저장까지 책임진다.
- 조회가 먼저 필요하면 앞단 Resolver가 Request를 해석하는 과정에서 처리할 수 있다.
- 여러 UseCase를 조합하는 흐름은 Orchestrator가 맡을 수 있다.
- 단순한 UseCase는 직접 조립할 수 있다.

## 모델 중심 중계

```text
Request 또는 의미 있는 파라미터 묶음
  → Validator: 입력 형식·필수값
  → Factory: 도메인 모델 생성
  → Policy: 모델 + 실행 컨텍스트의 업무·권한 규칙
  → Activity: 모델 하나 처리
  → ModelPipeline: 모델 기반의 선택적 다중 후속 단계
```

Factory 이후 Policy, Activity, ModelPipeline은 요청 DTO가 아니라 도메인 모델을 받는다.

- Validator와 Policy는 모델을 변경하지 않는다.
- 모델 변경은 Factory, Activity, 필요한 ModelStage만 수행한다.
- Validator는 null, 형식, 길이, 범위, 파싱 가능 여부처럼 입력 자체의 유효성을 검증한다.
- Policy는 유효한 모델에 대한 권한, 상태 전이, 한도, 업무 규칙처럼 행위의 허용 여부를 검증한다.
- Validator 실패는 입력 오류, Policy 실패는 업무상 거절로 표현한다.

## 요청 해석

```text
Request → Resolver (필요한 조회·해석) → Command 또는 직접 파라미터 → Orchestrator
```

Resolver는 모든 흐름에 강제되지 않는다. 요청을 Command 또는 직접 파라미터로 바꾸기 전에 조회나 해석이 필요할 때만 둔다.

- Resolver가 조회한 Aggregate Root를 Command에 담아 전달할지, 식별자·해석된 컨텍스트만 전달하고 Activity가 조회할지는 맥락에 따라 선택한다.
- 이미 전달된 Aggregate Root를 같은 흐름에서 다시 조회하지 않는다.
- 선택한 조회 위치와 이유는 UseCase 또는 Orchestrator에서 이해할 수 있게 남긴다.

## 선택적 Command와 라우팅

- Command는 Request를 해석·변경하여 Activity 메서드 파라미터로 전달할 의미 있는 값의 묶음이다.
- Command 객체는 필수가 아니다. Target, Collection 링크처럼 별도 객체가 의미를 더하지 않는 값은 명시적 타입 파라미터로 직접 전달한다.

- `Factory`는 객체 생성에만 사용한다. 조건 분기 위임 역할에는 쓰지 않는다.
- Dispatcher는 `getType()` 같은 명시적 키로 전략을 중계할 수 있다.
- `supports(...)` 방식은 필요한 선택 전략에서 사용할 수 있으며 전역 표준으로 강제하지 않는다.
- Dispatcher가 Command를 라우팅할 때 미등록 Command는 실패한다.
- 중복 전략의 단일·다중 실행은 전역 규칙으로 고정하지 않고 사용하는 중계 방식이 결정한다.
- 전략 분기는 향후 해당 처리만 이벤트 기반 흐름으로 분리할 수 있도록, Orchestrator의 나머지 흐름과 결합하지 않는다.

## 모델 다중 단계

실제 도메인 이벤트 리스너와 혼동하지 않도록, `supports(model)` 기반 다중 처리는 `ModelPipeline`과 `ModelStage`로 부른다.

```text
ModelPipeline
  → supports(model)가 true인 ModelStage를 순서대로 실행
```

## 이벤트

- 흐름 전체 완료에 대한 이벤트는 Orchestrator에 둘 수 있다.
- 특정 Aggregate Root에서 발생한 도메인 사실은 해당 Activity에 둘 수 있다.

## 트랜잭션 경계

- 각 쓰기 흐름은 트랜잭션 소유자를 Orchestrator 또는 Activity 중 하나로 명시한다.
- 여러 Aggregate Root 또는 외부 호출을 같은 트랜잭션에 묶지 않는다면 Outbox 또는 이벤트 기반 분리 여부를 명시한다.
- 트랜잭션 위치는 기능 맥락에 따라 선택하지만, 소유자와 경계는 생략하지 않는다.

## Hook 연계

- 구현 품질 스킬은 작성 중의 구조 점검에 사용한다.
- Hookify는 Java 변경 완료 직전에 이 스킬 실행을 경고한다.
- 최종 산출물 검토는 별도로 `java-principal-review`를 실행한다.

## 미결정 항목

- Validator, Policy, Factory, Activity, ModelPipeline의 상세 인터페이스와 호출 경계
- 트랜잭션 경계와 이벤트 발행의 영속성 보장 방식
- 구현 품질 스킬의 이름·명령·자연어 트리거
- Hookify 규칙의 정확한 이벤트·패턴·경고 문구
- Read 영역 규칙
