# T227 독립 검수

판정: 승인 범위의 구현은 수용 가능하다. Spec 94/100, Quality/Security 92/100으로 통합 기준 각각 90 이상이며, 미해결 P0 0건 / P1 0건 / P2 1건이다. 이는 현재 dirty revision의 한정된 NoIO 회귀 검수이며 배포 승인이나 실제 운영 연결 성공 판정이 아니다.

## 고정한 대상과 변경 범위

- 검토일: 2026-11-21.
- 작업트리: `<WORKTREE>`.
- branch: `task_T227-runtime-profile-startup`.
- 기준 SHA 및 HEAD: `5b6e2f87dd9d059972b12af9eb1d3245bf9f33e6`.
- `git diff --stat`: 제품 4개 파일, 5 insertions / 1 deletion.
- `git diff | sha256sum`: `d95eb78b343d80d4d0f74dd4b164c3d516d9444db00258ed48204be8a615f618`.

검토 제품 파일:

1. `backend/boot/src/main/java/com/example/discord/gateway/RedisGatewayEventBus.java`
2. `backend/services/message/build.gradle.kts`
3. `backend/services/websocket/build.gradle.kts`
4. `backend/services/websocket/src/main/resources/application-postgres.yml`

검토한 추가 파일과 SHA-256:

| 파일 | SHA-256 |
| --- | --- |
| `backend/boot/src/test/java/com/example/discord/gateway/RedisGatewayEventBusContextTest.java` | `212e2fc766683b35aea1bd566a67a4e880cf871a30c0e94ab13d2ffc41c2ee15` |
| `backend/services/message/src/test/java/com/example/discord/messageservice/ProductionProfileContextTest.java` | `3b424e2b54f60ded6d3ff8d448c75fd9a175aad4cc2c1aaed6477c6e90514c72` |
| `backend/services/websocket/src/test/java/com/example/discord/websocketservice/ProductionProfileContextTest.java` | `0b76b182af204e351dffc549c25037b7bb47de46a1cb6bc50a31e5d69f5b30db` |
| `docs/01-plan/features/T227-runtime-profile-startup.plan.md` | `9a3c987e4f382de7a69a00a2e219d644fc26f2899117c63749bd859dac774847` |

추가로 `docs/01-plan/features/T227-evidence/`의 선언된 로그·exit와 아래 최종 Gradle XML을 읽었다. 최초 `git status --short`는 위 제품 4개, 테스트 3개, 계획 및 증거 디렉터리만 표시했다. 여러 사용자 파일을 묶어 변경하거나 운영 프로필을 테스트용으로 바꾼 흔적은 이 diff에 없다. 검수자는 이 리뷰 파일만 작성했다.

## Blueprint Alignment

Matches blueprint: yes. 계획의 내부 리뷰 기준(85/80)보다 이번 위임의 통합 기준(각 90)을 우선 적용했다.

| 요구 | 코드/검증 근거 | 판단 |
| --- | --- | --- |
| Redis-only 실제 컴포넌트 생성 | 제품 79행 전체 설정 생성자 `@Autowired`; Redis 테스트 18–25행 실제 클래스 등록, refresh 및 실제 타입 확인 | 충족 |
| redis+kafka 제외 | 제품 35행 `redis & !kafka`; 테스트 32–35행 두 프로필에서 빈 없음 | 충족 |
| 두 실제 Application의 Kafka Boot 구성 | 두 Gradle 파일에 기존 boot 방식의 `spring-boot-kafka` 한 줄씩 추가; 각 테스트 15행 실제 Application, 25/26행 postgres+kafka | 충족 |
| KafkaTemplate·factory·consumer | message 37–45행, websocket 36–41행에서 실제 KafkaTemplate, 기본 factory 이름, 실제 소비자, 등록된 리스너 1개와 정지 상태 확인 | 충족 |
| password 실제 바인딩 | websocket YAML 7행 datasource 하위로 복구; 테스트 24행 합성 sentinel, 46–48행 실제 Environment Binder → DataSourceProperties 확인 | 충족 |
| NoIO | JDBC 자동 구성 제외와 JdbcTemplate mock, Flyway 비활성, Kafka listener auto-startup=false; message TaskScheduler mock으로 자동 relay 차단; Redis mock 무호출 확인 | 목적에 적합 |

테스트 우회는 테스트 annotation/필드 안에 있다. KafkaTemplate, factory, consumer를 mock하거나 운영 YAML에 NoIO 설정을 넣지 않았다. password 검사는 직접 `spring.datasource.password`를 주입하지 않아 YAML 들여쓰기 오류를 감춘 테스트가 아니다. Redis 테스트의 singleton mock 등록은 Spring lifecycle mock 호출을 제거하면서 실제 제품 생성자 선택은 그대로 검증한다.

Mismatch: 제품 동작 불일치 없음. 아래 P2는 보존된 RED 증거의 정밀도 문제다. Correct owner of fix: 구현자/증거 소유자. Required Loop Action: 제품 수정이나 blueprint 수정 불필요; 향후 RED 증거 보존 보강 권고. Wiki 수정/게시 여부는 coordinator 소유이며 본 검수에서는 외부 Vault를 읽거나 쓰지 않았다. 사용한 Wiki 페이지 없음(위임된 계획·diff·선언 증거 범위 준수).

## 명령과 증거 대조

검수자가 실행한 읽기/점검: `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git diff --stat`, `git diff`, `git diff --check`, `sha256sum`, 대상 파일의 `nl -ba`, 증거 `rg` 및 XML 조회. `git diff --check`는 exit 0이었다. 직접 shell 지정은 WSL `/bin/bash` 오류로 실패했으므로 PowerShell 런처에서 명시적 Git Bash `-lc`로 저장소 명령을 실행했다.

선언된 focused 명령:

```sh
./gradlew :backend:boot:test --tests '*RedisGatewayEventBusContextTest' :backend:services:message:test --tests '*ProductionProfileContextTest' :backend:services:websocket:test --tests '*ProductionProfileContextTest' --continue
```

| 기록 | exit/관찰 | 대조 결과 |
| --- | --- | --- |
| `red.log`, `red.exit` | 1; boot compileTestJava 실패, 두 서비스 context의 NoSuchBeanDefinitionException | boot의 최초 RED는 결함 입증으로 세지 않음; 서비스 RED 상세 타입은 P2 참조 |
| `redis-red.log`, `redis-red.exit`, `redis-red-cause.log` | 1; 실제 refresh에서 NoSuchMethodException, RedisGatewayEventBus 무인자 생성자 탐색 | 생성자 선택 결함과 직접 일치 |
| `password-red.log`, `password-red.exit` | 1; websocket password 48행 AssertionFailedError, 2개 중 1개 실패; message 통과 | Kafka 복구 후에도 password 단독 assertion 실패가 분리됨 |
| 같은 password RED의 boot | NoInteractionsWanted 25행 | 제품 결함으로 세지 않음; singleton mock으로 고친 테스트 lifecycle 문제 |
| `green.log`, `green.exit` | 0, BUILD SUCCESSFUL; message UP-TO-DATE | 나머지 실행 성공, message는 직전 통과 재사용임을 구분 |
| `regression.log`, `regression.exit` | 0, BUILD SUCCESSFUL 2m20s, `test checkstyleMain checkstyleTest --continue` | 전체 회귀/스타일 게이트 통과 기록과 일치 |
| `classpath-before.log` / `classpath-after.log` | 양 서비스 모듈 없음 → spring-boot-kafka 4.1.1 runtimeClasspath | 최소 의존성 수정과 일치; 별도 exit 파일 없음 |
| `diff-check.log`, `diff-check.exit` | 빈 로그, 0 | 검수자의 재확인과 일치 |

최종 XML은 각각 다음 경로에서 확인했다.

- `backend/boot/build/test-results/test/TEST-com.example.discord.gateway.RedisGatewayEventBusContextTest.xml`: tests=2, failures=0, errors=0, skipped=0, timestamp 11:05:52 UTC.
- `backend/services/message/build/test-results/test/TEST-com.example.discord.messageservice.ProductionProfileContextTest.xml`: tests=1, failures=0, errors=0, skipped=0, timestamp 11:07:08 UTC.
- `backend/services/websocket/build/test-results/test/TEST-com.example.discord.websocketservice.ProductionProfileContextTest.xml`: tests=2, failures=0, errors=0, skipped=0, timestamp 11:07:15 UTC.

메서드명과 assertion은 최종 테스트 소스와 일치한다. 최종 GREEN 총 5개 회귀가 모두 통과하며, 서비스 XML 시각은 전체 회귀 실행 기록과 양립한다. 이전 RED XML은 보존되어 있지 않아 당시 테스트 소스가 최종 소스와 바이트 단위로 같았다는 주장은 하지 않는다. Redis lifecycle 테스트 수정은 계획에 공개되어 있다.

검수 중 Gradle은 재실행하지 않았다. 현재 GREEN은 로그와 XML로 이미 확인되며 재실행으로 보완할 유일한 빈틈은 과거 RED의 상세 예외 보존이 아니다. 이를 직접 재현하려면 승인되지 않은 제품/의존성 되돌리기가 필요하므로 하지 않았다. `./gradlew --version` 성공은 계획의 선언이고 별도 버전 로그는 증거 디렉터리에 없으므로 독립 실행 사실로 계산하지 않았다.

## 발견 사항

### P2-1: 서비스 Kafka RED의 누락 빈 타입을 기록만으로 확정할 수 없음

- 위치: `docs/01-plan/features/T227-evidence/red.log:125–128`, `:143–149`; 계획의 검증 근거 중 “KafkaTemplate 누락을 확인” 문장.
- 관찰: 보존 로그에는 NoSuchBeanDefinitionException 종류만 있으며 누락된 빈 이름/타입 및 전체 원인 메시지가 없다. 이후 최종 GREEN XML로 덮여 과거 RED XML도 남아 있지 않다.
- 영향: 서비스 실패가 정확히 KafkaTemplate 누락이었고 동일 assertion까지 도달했는지를 과거 기록만으로 독립 확정할 수 없다. 다만 before/after 의존성 기록, 실제 Application 테스트 및 최종 XML이 수정 효과를 뒷받침하므로 제품 결함 또는 P1로 보지 않는다. Redis/password RED는 원인 또는 assertion 위치가 분리되어 이 문제와 구별된다.
- 최소 개선: 다음 승인된 재현 시 전체 예외의 타입/메시지 또는 해당 RED XML을 민감 값 제거 후 별도 보존하고 실행 명령·소스 지문을 함께 기록한다. 기존 공유 소스를 되돌려 현재 검수자가 재현하지 않는다.
- 검증 기준: 양 서비스에서 `KafkaTemplate` 누락 원인이 명시된 RED와 동일 테스트 메서드의 GREEN XML을 짝지을 수 있어야 한다.

## Quality/Security와 후속 위험

새 로깅 코드는 없고 password assertion은 boolean 비교여서 실패 메시지에 실제 값을 표시하지 않는다. 선언 로그와 최종 두 서비스 XML에서 합성 sentinel, POSTGRES_PASSWORD 문자열 및 연결 실패/브로커 재접속 흔적을 검색했으며 해당 일치는 없었다. 이 검사는 읽은 증거에 한정되며 모든 환경에서 비밀이 절대 로그에 나오지 않는다는 보장은 아니다.

NoIO는 mock 및 비활성 설정에 의한 기동 검증이다. 네트워크 차단 계측이나 실제 외부 DB/Redis/broker 연결 검증은 포함하지 않는다. 특히 datasource 자동 구성 제외로 실제 드라이버 인증을 검증하지 않지만 password Binder 검증 목적에는 적절하다. 메시지 발행/수신, 스키마, 운영 자격증명, 배포 적합성은 후속 통합 검증 범위다. 기존 opt-in 외부 테스트 skip과 Gradle/Mockito 경고는 선언된 전체 회귀의 제한으로 유지한다.

## Acceptance

- Accepted blueprint: yes, 지정 승인 범위와 일치.
- Accepted implementation: yes, 현재 고정 지문 대상의 최소 수정.
- Accepted verification: yes, NoIO 범위로 한정; P2-1 증거 정밀도 제한 명시.
- Spec: **94/100** — 승인 요구 모두 구현, 과거 서비스 RED 원인 확정 제한으로 감점.
- Quality/Security: **92/100** — 최소 diff와 테스트 격리 적절, 로그 비밀 노출 관찰 없음; RED 원본 보존·실행 지문 추적성 제한으로 감점.
- 결함 수: **P0 0 / P1 0 / P2 1**. 통합 검수 기준 충족, 현재 변경 수용 가능.
- Publishable knowledge: 분리 서비스의 Boot Kafka 모듈 필요성과 password 경로 회귀, Redis 다중 생성자 선택의 기동 함정. 게시 및 별도 merge-ready 검증은 coordinator가 수행해야 하며 이 문서는 push/merge/배포를 실행하거나 승인하지 않는다.
