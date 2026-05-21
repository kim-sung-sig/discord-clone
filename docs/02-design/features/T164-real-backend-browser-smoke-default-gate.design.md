# T164 Real Backend Browser Smoke Default Gate Design

Date: 2026-05-21

## Design

The default real backend browser gate is:

```powershell
npm.cmd run e2e:real-backend
```

Root `package.json` maps that command to `node qa/real-backend-e2e.mjs`. The Node wrapper selects `powershell.exe` on Windows and `pwsh` elsewhere, then delegates to `qa/real-backend-e2e.ps1`.

For local default runs, the harness:

- uses `http://127.0.0.1:18080` to avoid common local backend port 8080 collisions
- uses Compose Postgres at `jdbc:postgresql://127.0.0.1:15432/discord`
- runs `qa/central-compose-health.ps1` before starting the backend
- disables Redis health for this Postgres/browser smoke with `MANAGEMENT_HEALTH_REDIS_ENABLED=false`
- stops both the Gradle wrapper process and the owned Java child process on completion

CI still starts the backend separately on 8080 and calls:

```powershell
pwsh qa/real-backend-e2e.ps1 -BackendUrl http://127.0.0.1:8080 -SkipServiceStart
```
