# T61 Redis Multi-node Gateway Fanout Smoke Feedback

Date: 2026-05-21

## Registered Follow-ups

| Task | Priority | Note |
| --- | --- | --- |
| T62 Gateway subscription reconciliation | P2 | Cross-node RESUME needs reconnect-time stream subscription registration for sessions created on another node. |
| T183 Redis Gateway source-node duplicate suppression policy | P2 | Redis records include `sourceNodeId`, but local publish plus Redis polling can still produce same-node duplicate delivery unless policy is explicit. |
