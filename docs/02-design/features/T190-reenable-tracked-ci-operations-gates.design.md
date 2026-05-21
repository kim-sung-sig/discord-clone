# T190 Re-enable Tracked CI Operations Gates Design

Created: 2026-05-21
PDCA Phase: Design

## CI Job

Add a `qa-security` job to `.github/workflows/ci.yml`:

- Ubuntu runner.
- Java 21 and Node 22 setup.
- `npm ci`.
- `chmod +x ./gradlew`.
- `pwsh qa/security-gate.contract.ps1`.
- `pwsh qa/security-gate.ps1`.
- Upload `qa/artifacts/security` as `security-gate-artifacts`.

## Tracking Guard

`qa/ci-workflow.contract.ps1` already scans workflow `qa/*.ps1` references with `git ls-files`. T190 extends the required snippets so the security job cannot be removed silently.

Because this guard checks git tracking, final verification must run after staging task-owned paths.

## Agent Harness

Add narrow allowlist tools:

- `security-gate-contract`
- `security-gate`

Agents must use these instead of constructing custom scanner commands.

## Security Boundary

The CI job may query npm audit and OSV in an environment where dependency metadata transmission is allowed. Those advisory lookups require sending dependency package names/versions and SBOM-derived package data to external services. Local execution under the current tenant policy is not allowed, so local completion can only use contract/policy-only checks plus an explicit temporary waiver.

The gate must keep artifacts under `qa/artifacts/security` and must not log secrets. The allowlist remains explicit and expiry-checked by policy-only contract fixtures.
