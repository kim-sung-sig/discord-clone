# T09 Attachments/Storage Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T09 Attachments/Storage

## Executive Summary

| 관점 | 결과 |
| --- | --- |
| Problem | 메시지 기능에 파일 검증, safe object key, presigned URL, preview, orphan cleanup 모델이 없어 미디어 공유가 불가능했다. |
| Solution | `backend:modules:storage`와 `/api/attachments/*` REST adapter를 추가하고, Nuxt composer에 deterministic attachment preview/send UX를 연결했다. |
| Function UX Effect | 사용자는 이미지 첨부를 stage하고 preview를 확인한 뒤 메시지에 attachment metadata를 포함해 전송할 수 있다. |
| Core Value | MinIO/S3, CDN, attachment persistence, sticker/media 기능으로 확장할 안전한 storage boundary가 마련됐다. |

## Verification Evidence

Commands:

```powershell
.\gradlew.bat :backend:modules:storage:test :backend:boot:test --tests com.example.discord.storage.AttachmentControllerTest --rerun-tasks
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
npm run e2e -w apps/web -- tests/e2e/app-shell.spec.ts --grep attachment
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run
npm run build -w apps/web
npm run e2e -w apps/web
```

Results:

- T09 backend targeted: `BUILD SUCCESSFUL in 14s`; 29 actionable tasks executed
- T09 frontend targeted component: 1 file passed, 18 tests passed
- T09 frontend targeted e2e: 1 Playwright test passed
- Backend full: `BUILD SUCCESSFUL in 42s`; 49 actionable tasks executed
- Frontend component full: 4 files passed, 25 tests passed
- Frontend build: Nuxt production build completed with known sourcemap/Vue package warnings
- Frontend e2e full: 10 Playwright tests passed

## Commits

- `88c7710 docs: plan T09 attachments storage`
- `43de117 feat: add storage backend domain api`
- `a695bd3 feat: add attachment composer preview`
