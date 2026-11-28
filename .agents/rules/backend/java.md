---
id: backend-java-coding
globs:
  - "backend/**/*.java"
appliesTo: [backend]
---

# Java 코딩 규약

적용 범위는 `backend/`의 새 코드와 수정하는 코드다. [백엔드 규칙](../../../backend/AGENTS.md)의 경계·보안·검증 규칙을 함께 적용한다.

## 파일과 타입

- 공개 최상위 타입은 파일 하나에 하나씩 두고 타입명과 파일명을 맞춘다. 기존 경로의 들여쓰기와 임포트 형식을 따른다.
- 클래스·레코드는 PascalCase, 메서드와 변수는 camelCase, 상수는 UPPER_SNAKE_CASE를 쓴다. 도메인 이름은 [컨텍스트 맵](../../../CONTEXT-MAP.md)의 용어를 쓴다.
- HTTP 요청·응답과 단순 불변 값에는 `record`를 우선한다. 컬렉션을 불변 값으로 제공할 때는 생성 시 방어적으로 복사한다. 의존성은 생성자로 받고 재할당할 필요가 없는 필드는 `final`로 둔다.
- 조회 결과가 없을 수 있음을 표현할 때 기존 호출 계약과 맞는다면 반환형으로 `Optional<T>`를 사용한다. 예외를 변환할 때는 원인 예외를 보존한다. 여러 줄 SQL은 읽기 쉬울 때 Java 텍스트 블록을 사용한다.

## 모듈과 트랜잭션

- HTTP DTO와 인증 정보의 해석은 런타임 어댑터가 맡는다. 호출자는 모듈이 공개한 명령과 진입점을 사용한다. 도메인 코드는 Boot 어댑터 및 Spring·Jakarta 프레임워크에 의존하지 않는다.
- 하나의 업무 쓰기에 포함되는 DB 작업은 동일한 트랜잭션에 참여해야 한다. T230 파일럿부터 Spring 관리 빈을 통한 유스케이스 진입점에서 경계를 정의하고 실제 PostgreSQL 롤백을 검증한다. 기존 수동 경계의 이전 순서는 [T230 계획](../../../docs/01-plan/features/T230-declarative-transactions.plan.md)을 따른다.

## 확인 방법

| 항목 | 확인 |
| --- | --- |
| 도메인의 Boot·프레임워크 의존 | `./gradlew :backend:boot:test --tests "*.ArchitectureTest"` |
| 저장소 스타일 및 프로세스 계약 | [스타일 가이드](../../../docs/03-tasking/style-architecture-governance.md)의 Harness Gates |
| 이름·불변성·공개 진입점·트랜잭션 의미 | 코드 리뷰와 변경 기능 테스트. Java 파일 관례 전반을 자동 검사하는 단계는 아직 없다. |

참고한 원문은 ECC의 [Java coding style](https://github.com/affaan-m/ECC/blob/main/rules/java/coding-style.md)과 [Java patterns](https://github.com/affaan-m/ECC/blob/main/rules/java/patterns.md)다.
