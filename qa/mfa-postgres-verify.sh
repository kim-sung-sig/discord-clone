#!/usr/bin/env bash
# Opt-in real PostgreSQL verification for the identity MFA slice (TOTP + native WebAuthn persistence).
# The test JVM owns a disposable Testcontainers postgres:16-alpine database (plus the testcontainers/ryuk
# cleanup container). It never touches infra/docker compose databases or any pre-existing container,
# volume, or image; this script proves that by comparing `docker ps -aq` before and after the run.
#
# Usage: qa/mfa-postgres-verify.sh [extra gradle args]
# Exit codes: 0 success, 2 Docker daemon unavailable, 3 pre-existing container disappeared or task
# container left behind, otherwise the Gradle exit code.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! docker info >/dev/null 2>&1; then
    echo "mfa-postgres-verify: Docker daemon is not reachable; start Docker Desktop and retry." >&2
    exit 2
fi

before_ids="$(docker ps -aq --no-trunc | sort)"
echo "mfa-postgres-verify: pre-existing containers=$(printf '%s\n' "${before_ids}" | sed '/^$/d' | wc -l | tr -d ' ')"

export DISCORD_RUN_POSTGRES_TESTS=true
set +e
./gradlew :backend:services:identity:cleanTest :backend:services:identity:test \
    --tests com.example.discord.identityservice.PostgresMfaPersistenceTest \
    --tests com.example.discord.identityservice.PostgresMfaRecoveryPersistenceTest "$@"
status=$?
set -e

# ryuk removes the task containers (and itself) a few seconds after the test JVM disconnects; wait bounded.
# Label-independent check: every container that did not exist before this run must be gone.
# Pre-existing containers (including other worktrees' Testcontainers sessions) are excluded via before_ids.
leftover=""
for _ in $(seq 1 30); do
    leftover="$(comm -13 <(printf '%s\n' "${before_ids}") <(docker ps -aq --no-trunc | sort) | sed '/^$/d')"
    [ -z "${leftover}" ] && break
    sleep 1
done
after_ids="$(docker ps -aq --no-trunc | sort)"
missing="$(comm -23 <(printf '%s\n' "${before_ids}") <(printf '%s\n' "${after_ids}") | sed '/^$/d')"

echo "mfa-postgres-verify: gradle exit=${status}"
echo "mfa-postgres-verify: report backend/services/identity/build/reports/tests/test/index.html"
echo "mfa-postgres-verify: junit xml backend/services/identity/build/test-results/test/"
if [ -n "${missing}" ]; then
    echo "mfa-postgres-verify: FAIL pre-existing containers disappeared: ${missing}" >&2
    exit 3
fi
if [ -n "${leftover}" ]; then
    echo "mfa-postgres-verify: FAIL task-owned containers still present (ryuk cleanup pending?): ${leftover}" >&2
    exit 3
fi
echo "mfa-postgres-verify: cleanup ok (no pre-existing container lost, no task-owned container left)"
exit "${status}"
