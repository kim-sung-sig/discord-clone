# T07 Friendship/DM/Group DM Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T07 Friendship/DM/Group DM

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| Friend request lifecycle | Met | `InMemorySocialServiceTest` and `SocialControllerTest` cover request/accept/decline policy |
| Block/privacy | Met | Domain and MockMvc tests prove blocked users cannot create/send DM skeleton messages |
| DM channel | Met | Deterministic 1:1 DM channel and REST create endpoint implemented |
| Group DM | Met | Owner-led group DM aggregate with add/remove authorization implemented and tested |
| Group call state skeleton | Met | Group call active/participants skeleton exists in backend and Nuxt UI |
| DM list UI test | Met | Vitest app shell assertions cover DM sidebar, blocked row, group members, and call state |
| Group DM e2e | Met | Playwright opens group DM, adds/removes `qa-scout`, and starts call skeleton |

## Gap Log

- Resolved: no private relationship model existed; added `backend:modules:social`.
- Resolved: blocked users could not be represented in channel policy; `SocialPolicyException` now gates friend requests and DM sends.
- Resolved: group DM membership had no owner authorization boundary; owner-only add/remove enforced.
- Resolved: shell had no DM entry point; `DmSidebar` and Pinia social state/actions added.

## Residual Risks

- DM message persistence is an acceptance skeleton only; durable DM message body storage should be integrated with the message module in a later slice.
- Social state is in-memory and not yet persisted to Postgres.
- Group call state has no media transport or Gateway fanout yet.
