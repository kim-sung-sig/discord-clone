# T99 CSP Telemetry Retention Policy Feedback

## Improvement Tasks Captured

### T114 CSP Telemetry Retention Metrics

Track how many CSP telemetry records are pruned by age and max-entry cleanup so operators can understand data loss and tune retention settings.

### T115 SQLite Telemetry Maintenance Command

Add an operator maintenance command for SQLite telemetry, including manual prune, count summary, and optional `VACUUM` guidance for long-running local deployments.

