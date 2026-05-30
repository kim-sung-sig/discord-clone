# T120 Agent Harness PDCA Loop Feedback

Created: 2026-05-19
PDCA Phase: Act
Slice: T120 Agent Harness PDCA Loop

## Feedback Items

| Source | Finding | Decision |
| --- | --- | --- |
| Implementation review | The repo already had individual QA harnesses and PDCA docs. | Added only the missing orchestration layer instead of duplicating existing scripts. |
| Harness safety | Arbitrary command or argument passthrough would be unsafe for agents. | Use fixed Tool IDs only; add new narrow Tool IDs for variants. |
| Backlog update | Initial generated backlog row landed after the narrative section. | Updated `qa/new-ticket.ps1` to insert rows before `## Source Clusters`. |

## PDCA Act Decision

Accept this slice as a narrow automation foundation. Future work should add new Tool IDs only with contract coverage and a clear safety reason.
