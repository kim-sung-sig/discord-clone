# Agent Loop State

This file documents the shared state shape for the local agent PDCA loop. Runtime state is written by `qa/agent-harness.ps1` to `qa/artifacts/agent-harness/agent-harness-state.json`.

```json
{
  "activeTask": "T120",
  "phase": "Do",
  "lastTool": "ci-workflow-contract",
  "lastResult": "PASS",
  "nextAction": "Record evidence in PDCA analysis/report.",
  "blocked": false
}
```

## Fields

| Field | Meaning |
| --- | --- |
| `activeTask` | Current PDCA task id, usually from `AGENT_TASK_ID`. |
| `phase` | Current PDCA phase, usually from `AGENT_PDCA_PHASE`. |
| `lastTool` | Last allowlisted harness tool id that ran. |
| `lastResult` | `PASS` or `FAIL`. |
| `nextAction` | Recommended next action after the tool result. |
| `blocked` | `true` when the last tool failed and feedback/fix work is required. |

## Policy

- The Markdown file defines the stable state contract.
- The JSON artifact records the latest local execution state.
- Do not commit runtime JSON artifacts from `qa/artifacts/`.
