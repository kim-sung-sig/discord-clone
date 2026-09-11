---
slug: T105-admin-rbac-security-dashboard
ticket: T105
phase: feedback
hub: "[[T105-admin-rbac-security-dashboard]]"
---
> 🧭 [[T105-admin-rbac-security-dashboard]] · PDCA: [[T105-admin-rbac-security-dashboard.plan]] → [[T105-admin-rbac-security-dashboard.design]] → [[T105-admin-rbac-security-dashboard.analysis]] → [[T105-admin-rbac-security-dashboard.report]] → **feedback**

# T105 Admin RBAC Security Dashboard Feedback

## Improvement Tasks Captured

### T111 Backend Global Admin Role Contract

Define and expose a backend-level security admin contract so `/api/users/@me` or a dedicated admin-check endpoint can return explicit admin authority. This removes the need to manage dashboard admin access by user id allowlist.

### T112 Security Dashboard Operator Token UX

Add a minimal operator unlock flow for `/security` that stores the operator token in `sessionStorage` under `dc_security_dashboard_operator_token`. This should support break-glass access without requiring manual browser console edits.

### T113 Production Guard Configuration Check

Add a startup/build-time production warning or health check when the security dashboard has no backend auth URL, JWT secret, admin allowlist, or operator token configured.

