# T226 DB 연결 풀 설계

- 상태: 대화에서 설계 승인, 문서 검토 대기
- 목적: boot와 identity의 기존 DB 연결 설정을 유지하면서 연결을 재사용한다.

## 범위와 설계

- boot의 postgres 프로필 `PostgresPersistenceConfiguration`과 identity의 MFA 활성화 시 `MfaConfiguration`에서 기존 DataSource 대신 HikariDataSource를 생성한다. 기존 URL, 사용자, 비밀번호 및 프로필 조건은 유지한다.
- 두 서비스 각각 최대 연결 수는 기본 10개이며 환경별 설정으로 변경할 수 있다. 각 풀의 이름을 구분한다. 10은 초기 기본값이며 운영에서는 인스턴스 수와 다른 DB 사용자를 포함한 전체 연결 수가 PostgreSQL 연결 한도 이내가 되도록 조정한다.
- HikariCP 의존성을 필요한 모듈에 추가하고 기존 Spring Boot BOM의 버전 관리를 따른다. 기본 프로필의 자동 구성 동작을 바꾸지 않는다.
- 기존 소비자, Flyway, 트랜잭션 관리자가 동일 DataSource를 사용한다. Spring 컨텍스트 종료 시 풀이 닫혀야 한다.
- message와 websocket은 기존 풀 사용 여부만 확인한다. 다른 SQL, 트랜잭션 로직, JPA 전환은 범위 밖이다.

## 장애와 검증

- 잘못된 풀 설정이나 DB 연결 실패를 숨기지 않고 기존 호출 경로에 실패를 전달한다. 풀 고갈 시 설정된 연결 대기 시간이 지나면 호출이 실패한다.
- DB 없는 테스트로 두 DataSource의 유형, 접속 정보, 기본값과 설정 변경, 컨텍스트 종료 시 풀 종료를 확인한다. 기존 MFA 관련 시험과 postgres 프로필 계약 시험으로 실제 연결 및 기존 저장 동작을 확인한다.
- Gradle wrapper 버전과 관련 테스트를 실행한다. 실제 PostgreSQL 시험을 실행할 수 없다면 이유와 미검증 범위를 기록한다. message와 websocket의 실제 풀 구현은 의존성 또는 실행 결과로 확인한다.

## 예상 변경 파일

- `backend/boot/build.gradle.kts`, `backend/boot/src/main/java/com/example/discord/persistence/PostgresPersistenceConfiguration.java`, `backend/boot/src/main/resources/application-postgres.yml` 및 관련 테스트
- `backend/services/identity/build.gradle.kts`, `backend/services/identity/src/main/java/com/example/discord/identityservice/MfaConfiguration.java`, 관련 설정 및 테스트
- 구현 계획과 검증·리뷰 증거 문서

기존 사용자 미추적 파일은 이 작업에 포함하지 않는다.
