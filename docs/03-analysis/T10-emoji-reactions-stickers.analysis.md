# T10 Emoji/Reactions/Stickers Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T10 Emoji/Reactions/Stickers

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| emoji CRUD | Met | `InMemoryExpressionService` and `ExpressionControllerTest` cover create/list/delete skeleton metadata |
| reaction add/remove/list | Met | domain and REST tests cover idempotent add, remove safety, and list summaries |
| sticker skeleton | Met | service/controller tests cover create/list sticker metadata |
| expression permissions | Met | `MANAGE_EXPRESSIONS` permission and MockMvc permission test |
| duplicate reaction idempotency | Met | domain and REST duplicate reaction count remains 1 |
| custom emoji permission | Met | non-manager forbidden, manager role allowed |
| reaction UI test | Met | Vitest and Playwright cover reaction toggle/count behavior |

## Gap Log

- Resolved: reaction endpoint originally allowed arbitrary existing channel + nonexistent message IDs. Added failing test and message existence validation through `InMemoryMessageService`.
- Resolved: expression creation had no dedicated permission. Added `MANAGE_EXPRESSIONS` and `canManageExpressions`.
- Resolved: duplicate reaction count could be modeled as increments. Domain stores unique reactor IDs and derives counts.
- Resolved: frontend reaction UI needed store-backed mutation. Added `ReactionBar`, `ExpressionPanel`, and Pinia actions.

## Residual Risks

- Emoji/sticker binary processing is metadata skeleton only; storage integration is deferred.
- Reaction persistence is in-memory; database uniqueness constraints will be required later for multi-node race safety.
- Gateway fanout for reactions is deferred.
