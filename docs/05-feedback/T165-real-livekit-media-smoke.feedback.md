# T165 Real LiveKit Media Smoke Feedback

Date: 2026-05-21

## Captured Improvements

| Task | Priority | Note |
| --- | --- | --- |
| T181 LiveKit media smoke CI service automation | P3 | The real media smoke is runnable and locally verified, but CI currently enforces the contract only. A future CI job can start LiveKit and the media-livekit backend to run it automatically. |

## Security Note

Keep real media smoke artifacts token-safe. Do not upload raw issued LiveKit JWTs or API secrets when CI automation is added.
