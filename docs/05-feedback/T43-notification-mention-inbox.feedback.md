# T43 Notification & Mention Inbox Feedback

작성일: 2026-05-18  
Slice: T43 Notification & Mention Inbox

## Feedback Items

| ID | Severity | Finding | Follow-up |
| --- | --- | --- | --- |
| T43-FB-001 | High | Notification inbox is domain-only and has no REST API. | T68 notification REST API. |
| T43-FB-002 | High | Notification items are in-memory only. | T69 notification persistence. |
| T43-FB-003 | Medium | Frontend has no notification center UI. | T70 notification inbox UI. |
| T43-FB-004 | Medium | PWA/browser notification adapter is not implemented. | T71 PWA/browser notification adapter. |
| T43-FB-005 | Medium | Mention parsing is upstream; notification receives parsed recipient ids. | Add message-to-notification integration when REST/persistence boundary is introduced. |
