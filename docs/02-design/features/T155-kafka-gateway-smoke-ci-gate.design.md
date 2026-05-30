# T155 Kafka Gateway Smoke CI Gate Design

Date: 2026-05-20
Slice: T155 Kafka Gateway Smoke CI Gate

## Design

The CI gate is a dedicated `qa-central-kafka` job in `.github/workflows/ci.yml`.

## Job Steps

1. Checkout repository.
2. Set up Java 21 with Gradle cache.
3. Make `./gradlew` executable.
4. Run central compose and Kafka Gateway smoke contracts.
5. Run `pwsh qa/central-kafka-gateway-smoke.ps1` with `SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:29092`.

## Script Portability

`qa/central-kafka-gateway-smoke.ps1` now resolves the Gradle wrapper at runtime:

- Windows: `gradlew.bat`
- Non-Windows: `gradlew`

Native commands are invoked through a helper that throws on non-zero exit codes.
