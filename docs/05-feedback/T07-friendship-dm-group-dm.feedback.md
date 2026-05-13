# T07 Friendship/DM/Group DM Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend TDD | Friend request and DM policy needed to reject block relationships before channel creation/send. | Added bidirectional block checks to friend request and DM send policy. |
| Backend TDD | Group DM member mutation needed an explicit owner boundary. | Added owner-only add/remove and owner removal rejection. |
| Frontend TDD | DM UI needed to prove it was not static decoration. | Added Pinia social actions and tests that mutate active group members/call state. |
| Integration QA | Full Story index needed to include the new social component. | Added `DmSidebar.stories.ts` and story-index coverage. |

## Known Non-Blocking Risks

- DM message persistence is still a skeleton receipt and should be integrated with the message module when private channel persistence is introduced.
- Social state remains in-memory until the database migration slice.
- Group call skeleton does not include WebRTC, SFU, or Gateway fanout yet.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
