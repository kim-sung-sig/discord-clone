# T26 Nuxt SSR CSP Nonce Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Nuxt HTML script `unsafe-inline` with request-scoped CSP nonces while preserving SSR hydration.

**Architecture:** Keep CSP generation and script tag mutation as testable utilities. Use a Nitro plugin to generate nonce per response, mutate rendered HTML arrays, and set matching security headers.

**Tech Stack:** Nuxt 4, Nitro server plugin, h3 response headers, Vitest, Playwright.

---

### Task 1: Nonce-aware CSP Unit Tests

**Files:**
- Modify: `apps/web/tests/components/security-headers.test.ts`
- Modify later: `apps/web/server/utils/security-headers.ts`

- [ ] Add tests for nonce-backed `script-src` and absence of script `unsafe-inline`.
- [ ] Add tests for script tag nonce injection helper.
- [ ] Run targeted test and verify RED.

### Task 2: CSP Nonce Utilities and Nitro Plugin

**Files:**
- Modify: `apps/web/server/utils/security-headers.ts`
- Create: `apps/web/server/plugins/csp-nonce.ts`
- Modify: `apps/web/server/middleware/security-headers.ts`

- [ ] Add `htmlSecurityHeaders({ scriptNonce })` option.
- [ ] Add `addNonceToScriptTags` helper.
- [ ] Add Nitro plugin that generates nonce, mutates rendered HTML, and sets response headers.
- [ ] Run targeted tests and verify GREEN.

### Task 3: Runtime Verification

**Files:**
- Existing tests only.

- [ ] Run frontend unit tests.
- [ ] Run login/app-shell Playwright with isolated port.
- [ ] Run real-backend QA harness.
- [ ] Run frontend build.

### Task 4: PDCA Check/Report

**Files:**
- Create: `docs/03-analysis/T26-nuxt-ssr-csp-nonce-hardening.analysis.md`
- Create: `docs/04-report/T26-nuxt-ssr-csp-nonce-hardening.report.md`
- Modify: `.bkit-memory.json`

- [ ] Record verification evidence and residual risks.
- [ ] Commit report docs.
