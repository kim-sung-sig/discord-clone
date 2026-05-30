# T159 Compose Health Diagnostic Failure Smoke Analysis

Date: 2026-05-20
Slice: T159 Compose Health Diagnostic Failure Smoke

## Findings

- T156 diagnostic output existed, but only the success path was verified by normal health runs.
- A destructive failure test is unnecessary; a guarded forced failure can prove the same diagnostic printer path.
- Capturing a failing PowerShell child process requires temporarily relaxing `$ErrorActionPreference` in the wrapper so partial output is retained.
- The normal health path still passes after adding the forced diagnostic branch.

## Security Review

- The forced diagnostic mode does not read or print application tokens.
- Diagnostic output includes Docker and Compose state; CI artifact visibility should remain restricted to project operators.
- Running the normal health path still prints the existing Redis CLI warning about command-line password usage. This is a QA hardening issue and is tracked as a follow-up.

## Residual Risk

- The diagnostic smoke proves marker and path behavior, not every possible Docker failure mode.
- Redis password handling in QA scripts still deserves cleanup to avoid command-line secret exposure patterns.
