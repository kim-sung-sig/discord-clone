# T166 Discord Shell Layout Compression Pass Analysis

Date: 2026-05-21
Status: Completed

## Findings

- The first visual smoke attempt reused an unrelated app on `localhost:3000`, so T166 verification now uses isolated ports.
- The RED layout guard detected horizontal overflow in gateway status cards, role permission controls, and voice controls.
- Screenshot review after the first CSS pass showed remaining text collisions in the admin preview permission diff and clipped moderation audit labels.
- The final CSS pass made these text groups block/grid content and extended the Playwright guard to include permission diff, preview-as-role, privileged audit, and moderation audit entries.

## Security And Reliability Review

- No secrets or backend authorization surfaces changed.
- The new screenshot path is under local `output/playwright`, not a runtime-served asset.
- The isolated-port verification pattern reduces false positives from accidentally testing another local service.

## Follow-up Task Registered

- T189 Playwright local port isolation guard: make the e2e harness fail fast or self-isolate when port 3000 is already occupied by a non-project app.
