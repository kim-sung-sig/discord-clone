# T111 Backend Global Admin Role Contract Feedback

## Improvement Tasks Captured

### T118 Global Admin Grant Operations Tool

Add an operationally safe way to grant and revoke `SECURITY_ADMIN`, either as an admin-only backend endpoint, a CLI task, or a documented migration/runbook flow. T111 adds the data contract but not the production grant workflow.

### T119 Global Admin Audit Log

Record grants and revocations of global roles in an audit table so security dashboard authority changes are traceable.

