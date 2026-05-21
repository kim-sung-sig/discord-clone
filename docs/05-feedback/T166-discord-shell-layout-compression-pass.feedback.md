# T166 Discord Shell Layout Compression Pass Feedback

Date: 2026-05-21
Status: Completed

## What Worked

- A focused Playwright measurement test caught the exact clipped-panel failure that screenshot review exposed.
- Keeping the desktop information architecture intact avoided a larger product redesign.
- Isolated Nuxt ports prevented false-positive browser runs against unrelated local apps.

## Improvement Captured

- T189 should harden the Playwright local server workflow so future visual checks cannot silently reuse a non-project service on port 3000.

## Next Task

Proceed to T55 restore snapshot hash comparison unless a higher-priority user request interrupts the queue.
