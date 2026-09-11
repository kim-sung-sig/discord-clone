---
slug: T145-remove-runtime-in-memory-persistence-defaults
ticket: T145
phase: feedback
hub: "[[T145-remove-runtime-in-memory-persistence-defaults]]"
---
> 🧭 [[T145-remove-runtime-in-memory-persistence-defaults]] · PDCA: [[T145-remove-runtime-in-memory-persistence-defaults.plan]] → [[T145-remove-runtime-in-memory-persistence-defaults.design]] → [[T145-remove-runtime-in-memory-persistence-defaults.analysis]] → [[T145-remove-runtime-in-memory-persistence-defaults.report]] → **feedback**

# T145 Remove Runtime In-Memory Persistence Defaults Feedback

Date: 2026-05-20
Slice: T145 Remove Runtime In-Memory Persistence Defaults

## Improvement Tasks Captured

### T150 Production Profile Guard Smoke Test

Add a QA or CI smoke that starts the backend with `production` but without `postgres` and asserts the runtime guard fails with the expected message.
