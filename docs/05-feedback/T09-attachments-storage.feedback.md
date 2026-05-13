# T09 Attachments/Storage Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend TDD | Upload validation must reject oversize and unsupported content type before URL issuance. | Added `AttachmentUploadPolicy` and controller validation coverage. |
| Backend TDD | Client must not be able to choose arbitrary object keys. | Object keys are generated server-side and scoped by owner/channel/attachment id. |
| Backend TDD | Uploaded objects without message attachment can become orphans. | Added status-based orphan cleanup with injected clock. |
| Frontend TDD | Attachment preview must be store-backed, not static decoration. | Added staged attachment state, preview component, send metadata, and send-clears-stage tests. |

## Known Non-Blocking Risks

- MinIO/S3 SDK integration is deferred behind `ObjectStore`.
- Attachment metadata is in-memory and not persisted to Postgres yet.
- Virus scanning, multipart upload, CDN signing, and image thumbnail generation are deferred.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
