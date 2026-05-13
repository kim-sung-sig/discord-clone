# T09 Attachments/Storage Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T09 Attachments/Storage

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| presigned upload | Met | backend storage service/controller returns server-generated upload skeleton URL |
| attachment metadata | Met | metadata model/status and frontend sent attachment metadata state |
| download URL | Met | backend scoped download URL issuance with owner/channel checks |
| image preview | Met | Nuxt `AttachmentPreview` renders staged image metadata/preview |
| file validation | Met | backend size/type validation tests |
| orphan cleanup | Met | backend uploaded orphan cleanup tests |
| attachment send e2e | Met | Playwright stages and sends deterministic image attachment from composer |

## Gap Log

- Resolved: client-controlled object keys risk; storage service now generates scoped keys server-side.
- Resolved: invalid size/type uploads had no policy; upload policy validates before issuing upload URL.
- Resolved: uploaded-but-unattached objects could remain forever; orphan cleanup uses deterministic clock/status.
- Resolved: composer had no attachment UX; staged image preview and sent metadata rendering added.

## Residual Risks

- Real MinIO/S3 SDK integration is deferred; current object store is in-memory and port-based.
- Message attachment persistence is shell/domain skeleton, not database-backed.
- Virus scanning, multipart upload, CDN cache, and image transformations are not yet implemented.
