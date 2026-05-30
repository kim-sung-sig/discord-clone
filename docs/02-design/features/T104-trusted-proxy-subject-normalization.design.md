# T104 Trusted Proxy Subject Normalization Design

## Subject Selection

The CSP rate-limit subject is selected in this order:

1. If the direct remote address is not a configured trusted proxy, use the direct remote address.
2. If the direct remote address is trusted, use the first valid IP from `x-forwarded-for`.
3. If `x-forwarded-for` has no valid IP, use `x-real-ip`.
4. If no valid address exists, use `unknown`.

## Configuration

`NUXT_CSP_RATE_LIMIT_TRUSTED_PROXY_CIDRS` is a comma-separated allow list.

- Exact IP values are accepted.
- IPv4 CIDR values are accepted.
- Invalid entries are ignored.
- Empty config means forwarded headers are ignored.

## Route Integration

Both CSP report routes use the shared utility:

- `/api/security/csp-report`
- `/api/security/csp-report-only`

The limiter, telemetry, and CSP report normalization logic remain unchanged.
