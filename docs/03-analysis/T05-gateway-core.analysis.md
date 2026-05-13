# T05 Gateway Core Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Slice: T05 gateway core

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| Authenticated identify and READY | Met | Gateway service and MockMvc identify tests |
| Heartbeat ACK and timeout | Met | service heartbeat ACK and closed-session timeout tests |
| Event sequence monotonicity | Met | service monotonic sequence tests |
| Resume after sequence | Met | service and MockMvc resume-after-sequence tests |
| Unauthorized channel event filtering | Met | service/MockMvc hidden channel filtering and publish authorization tests |
| Nuxt gateway store/status UI | Met | Vitest and Playwright gateway status assertions |

## Gap Log

- Resolved: new sessions initially saw historical backlog; identify now starts delivery after READY sequence.
- Resolved: timed-out sessions remained usable; active heartbeat/poll/resume now reject closed sessions.
- Resolved: authenticated users could publish events to arbitrary guilds; publish now requires guild membership and channel/guild match.
- Resolved: owners could receive forged channel events for nonexistent/mismatched channels; publish validates channel ownership before event creation.
- Resolved: frontend gateway event guard accepted stale lower sequences; store now rejects events with `sequence <= lastSequence`.
