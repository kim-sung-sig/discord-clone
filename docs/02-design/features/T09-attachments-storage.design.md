# T09 Attachments/Storage Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T09 Attachments/Storage

## Backend Architecture

Create `backend/modules/storage` for attachment metadata and safe object-key lifecycle. The module exposes a storage port so MinIO/S3 can replace the in-memory adapter later without changing controller tests.

Planned files:

- `backend/modules/storage/build.gradle.kts`
- `backend/modules/storage/src/main/java/com/example/discord/storage/Attachment.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/AttachmentStatus.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/AttachmentUploadPolicy.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/PresignedUpload.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/PresignedDownload.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/ObjectStore.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/InMemoryObjectStore.java`
- `backend/modules/storage/src/main/java/com/example/discord/storage/InMemoryAttachmentService.java`
- `backend/modules/storage/src/test/java/com/example/discord/storage/InMemoryAttachmentServiceTest.java`

Boot adapter files:

- `backend/boot/src/main/java/com/example/discord/storage/StorageConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/storage/AttachmentController.java`
- `backend/boot/src/test/java/com/example/discord/storage/AttachmentControllerTest.java`
- `settings.gradle.kts`, `backend/boot/build.gradle.kts`

API shape:

- `POST /api/attachments/uploads` body `{ "channelId": "...", "filename": "image.png", "contentType": "image/png", "sizeBytes": 1200 }` returns generated attachment id, object key, upload URL.
- `PUT /api/attachments/{attachmentId}/uploaded` marks object uploaded after storage callback/client completion skeleton.
- `POST /api/channels/{channelId}/messages/{messageId}/attachments/{attachmentId}` attaches uploaded metadata to message skeleton.
- `GET /api/attachments/{attachmentId}/download` returns scoped download URL.
- `DELETE /api/attachments/orphans` triggers deterministic cleanup for test/maintenance.

## Frontend Architecture

Extend composer with attachment selection state and preview without requiring actual file upload.

Planned files:

- `apps/web/components/shell/AttachmentPreview.vue`
- `apps/web/stores/shell.ts`
- `apps/web/components/shell/ChatViewport.vue`
- `apps/web/assets/css/main.css`
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/e2e/app-shell.spec.ts`

## Test Plan

- Backend unit: reject oversize/invalid type, server-generated object key, orphan cleanup after TTL.
- Backend MockMvc: authenticated upload request, invalid content type, download isolation.
- Frontend component: composer attachment preview and send clears staged attachment.
- Playwright: attach image skeleton, preview visible, send message shows attachment metadata.

## Risk Controls

- Never accept object key from client.
- Store owner/channel/message scope on metadata and validate before download URL issuance.
- Use injected clock for orphan cleanup tests.
