# T158 Kafka Gateway CI Failure Artifacts Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T158 Kafka Gateway CI Failure Artifacts

## Design

Reuse the T157 Redis failure artifact pattern with Kafka-specific names and broker logs.

| Artifact | Source | Purpose |
| --- | --- | --- |
| `docker-ps.txt` | `docker ps -a` | Container state and port bindings |
| `docker-compose-ps.txt` | `docker compose ps` | Compose service state |
| `docker-compose-config.txt` | `docker compose config` | Effective Compose topology |
| `docker-compose-ms-kafka.log` | `docker compose logs ms-kafka` | Broker logs through Compose |
| `docker-ms-kafka.log` | `docker logs ms-kafka` | Direct broker container logs |
| `gradle-test-report/` | `backend/boot/build/reports/tests/test` | Human-readable Gradle test report |
| `gradle-test-results/` | `backend/boot/build/test-results/test` | JUnit XML test results |

## CI Integration

- Run `qa/central-kafka-ci-artifacts.contract.ps1` in the Kafka smoke contract step.
- Set `CENTRAL_KAFKA_ARTIFACT_DIR` for the smoke and artifact collection steps.
- Run `qa/central-kafka-ci-artifacts.ps1` only when the Kafka job fails.
- Upload `qa/artifacts/central-kafka` only when the Kafka job fails.

## Risk Controls

- Artifact collection uses `ErrorActionPreference = Continue` and best-effort command capture so it does not hide the original failure.
- Artifacts stay under `qa/artifacts/`, which is ignored by git.
- The script copies existing Gradle outputs without changing the test invocation.
