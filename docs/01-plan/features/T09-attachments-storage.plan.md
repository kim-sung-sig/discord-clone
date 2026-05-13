# T09 Attachments/Storage Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T09 Attachments/Storage

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | 메시지에는 파일 첨부, 검증, 다운로드 URL, 이미지 preview, orphan cleanup 모델이 없어 Discord 핵심 미디어 공유 흐름을 검증할 수 없다. |
| Solution | 신규 `storage` backend module에 object-store port, presigned upload/download token, attachment metadata, validation, orphan cleanup을 구현하고 Nuxt composer에 attachment preview를 연결한다. |
| Function UX Effect | 사용자는 파일을 첨부하고 이미지 preview/metadata/download URL 상태를 확인할 수 있다. |
| Core Value | T10 emoji/sticker 및 이후 CDN/MinIO/S3 production integration이 재사용할 안전한 object key/metadata 기반을 만든다. |

## Scope

- Presigned upload skeleton: object key is server-generated and scoped to owner/channel.
- Attachment metadata: filename, content type, byte size, object key, status.
- Download URL skeleton: server validates ownership/channel visibility before issuing URL.
- Image preview: frontend renders image attachment preview state.
- File validation: max size and allowed content types.
- Orphan cleanup: uploaded-but-not-attached objects expire/cleanup deterministically.

## Out of Scope

- Real MinIO SDK integration; compose has MinIO, but T09 starts with replaceable object-store port and in-memory adapter.
- Virus scanning and media transcoding.
- CDN invalidation and multi-part uploads.

## Success Criteria

- Backend size/type validation tests pass.
- Backend object key access isolation test passes.
- Backend orphan cleanup test passes.
- Frontend attachment send e2e passes.
- Full gates pass.

## Failure Criteria

- Client can choose arbitrary object keys.
- Download URL can be issued to non-owner/non-visible user.
- Uploaded object remains orphaned after message attach failure/expiry.
- Frontend preview is static and not tied to composer/store state.

## Delivery Strategy

1. Backend storage domain TDD: metadata, validation, presigned token, orphan cleanup.
2. Boot attachment REST adapter TDD.
3. Nuxt attachment composer/preview TDD and Playwright send flow.
4. Full verification and PDCA documentation.
