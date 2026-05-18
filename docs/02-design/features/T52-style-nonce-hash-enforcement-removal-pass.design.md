# T52 Style Nonce/Hash Enforcement Removal Pass Design

Date: 2026-05-18
Slice: T52 Style Nonce/Hash Enforcement Removal Pass

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

The CSP generator currently accepts an internal `allowUnsafeInlineStyle` flag for enforce and report-only policies. T52 removes the permissive default and makes style policy strict by default:

```text
style-src 'self'
```

Both enforce and report-only policies use the same style directive. Report-only remains useful for additional future experiments, but it no longer carries the only strict style signal.

The change is intentionally localized to `security-headers.ts`. Existing nonce injection only affects script tags and remains unchanged.

## Testing

Tests assert:

- Enforce CSP contains strict style directive.
- Enforce CSP does not contain style `unsafe-inline`.
- Report-only CSP remains strict.
- Script nonce policy still excludes script `unsafe-inline`.

Build verification catches static Nuxt compatibility issues. Runtime visual/browser compatibility remains a follow-up because the current slice does not run the full e2e viewport matrix.
