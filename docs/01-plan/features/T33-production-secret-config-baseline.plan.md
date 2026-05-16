# T33 Production Secret & Config Baseline Plan

## Problem
The backend can still start production-like profiles with development defaults unless every operator remembers to override secrets manually.

## Plan
1. Add boot-time validation under `production` profile.
2. Keep local/CI fixture values explicit and documented.
3. Add redaction coverage for password/token/secret text.
4. Add `.env.example` and rotation guide.
5. Run backend tests and diff checks.

## Success Criteria
- Production profile fails when `postgres` is missing.
- Production profile fails on local/test default auth, gateway, and datasource secrets.
- Production profile starts with explicit non-default config.
- Redaction tests prove common token/password/secret forms are masked.
- Secret contract and rotation guidance are documented.

## Failure Criteria
- Production starts with default local secrets.
- CI or QA artifacts require raw production secret values.
- Redaction leaves bearer tokens or password query params visible.
