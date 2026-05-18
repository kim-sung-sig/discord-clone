import { createHash } from 'node:crypto'

export interface HtmlSecurityHeaderOptions {
  scriptNonce?: string
  connectSources?: string[]
  cspReporting?: CspReportingOptions
}

export interface CspReportingOptions {
  enforceEndpoint: string
  reportOnlyEndpoint: string
}

export interface NormalizedCspReport {
  requestId: string
  documentUriOrigin: string
  blockedUriOrigin: string
  violatedDirective: string
  effectiveDirective: string
  disposition: 'enforce' | 'report'
  userAgentHash: string
}

export interface CspReportContext {
  requestId: string
  userAgent: string
}

export interface CspReportNormalizationResult {
  accepted: boolean
  report?: NormalizedCspReport
  reason?: string
}

const scriptSource = (scriptNonce?: string): string => {
  if (scriptNonce && scriptNonce.trim().length > 0) {
    return `script-src 'self' 'nonce-${scriptNonce}'`
  }

  return "script-src 'self'"
}

const styleSource = (): string => "style-src 'self'"

const cspPolicy = (
  options: HtmlSecurityHeaderOptions,
  reporting?: { group: string; endpoint: string }
): string => [
    "default-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "img-src 'self' data: blob:",
    styleSource(),
    scriptSource(options.scriptNonce),
    connectSource(options.connectSources),
    ...(reporting ? [`report-to ${reporting.group}`, `report-uri ${reporting.endpoint}`] : [])
  ].join('; ')

export const htmlSecurityHeaders = (options: HtmlSecurityHeaderOptions = {}): Record<string, string> => {
  const headers: Record<string, string> = {
    'Content-Security-Policy': cspPolicy(
      options,
      options.cspReporting
        ? {
            group: 'csp-endpoint',
            endpoint: options.cspReporting.enforceEndpoint
          }
        : undefined
    ),
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy': 'camera=(), microphone=(self), geolocation=()'
  }

  if (options.cspReporting) {
    headers['Content-Security-Policy-Report-Only'] = cspPolicy(options, {
      group: 'csp-report-only',
      endpoint: options.cspReporting.reportOnlyEndpoint
    })
    headers['Reporting-Endpoints'] = [
      `csp-endpoint="${options.cspReporting.enforceEndpoint}"`,
      `csp-report-only="${options.cspReporting.reportOnlyEndpoint}"`
    ].join(', ')
  }

  return headers
}

const defaultConnectSources = [
  "'self'",
  'http://127.0.0.1:*',
  'http://localhost:*',
  'ws://127.0.0.1:*',
  'ws://localhost:*'
]

const connectSource = (sources: string[] = []): string => {
  const uniqueSources = [...new Set([...defaultConnectSources, ...sources.filter(Boolean)])]
  return `connect-src ${uniqueSources.join(' ')}`
}

export const connectSourcesFromUrls = (urls: Array<string | undefined>): string[] =>
  urls.flatMap((url) => {
    if (!url) {
      return []
    }
    try {
      return [new URL(url).origin]
    } catch {
      return []
    }
  })

export const addNonceToScriptTags = (htmlParts: string[], scriptNonce: string): string[] =>
  htmlParts.map((part) => part.replace(/<script(?![^>]*\bnonce=)/g, `<script nonce="${scriptNonce}"`))

const supportedReportContentTypes = new Set([
  'application/csp-report',
  'application/reports+json',
  'application/json'
])

export const maxCspReportBodyBytes = 16_384

export const shouldAcceptCspReport = (contentType: string | undefined, body: string): boolean => {
  const normalizedType = (contentType ?? '').split(';')[0]?.trim().toLowerCase()
  return supportedReportContentTypes.has(normalizedType) && Buffer.byteLength(body, 'utf8') <= maxCspReportBodyBytes
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const stringValue = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim().length > 0 ? value : undefined

const userAgentHash = (userAgent: string): string =>
  createHash('sha256').update(userAgent).digest('hex')

const originOnly = (value: unknown): string => {
  const raw = stringValue(value)
  if (!raw) {
    return 'unknown'
  }

  const literal = raw.trim().toLowerCase()
  if (literal === 'inline' || literal === 'eval' || literal === 'self' || literal === 'data' || literal === 'blob') {
    return literal
  }

  try {
    return new URL(raw).origin
  } catch {
    return 'unknown'
  }
}

const dispositionFor = (value: unknown): 'enforce' | 'report' =>
  stringValue(value)?.toLowerCase() === 'report' ? 'report' : 'enforce'

const firstReport = (payload: unknown): Record<string, unknown> | null => {
  if (Array.isArray(payload)) {
    const candidate = payload.find((entry) => isRecord(entry))
    if (!candidate) {
      return null
    }
    const body = candidate.body
    if (!isRecord(body)) {
      return null
    }
    return {
      'document-uri': candidate.url,
      'blocked-uri': body.blockedURL,
      'violated-directive': body.violatedDirective,
      'effective-directive': body.effectiveDirective,
      disposition: body.disposition
    }
  }

  if (!isRecord(payload)) {
    return null
  }

  const classicReport = payload['csp-report']
  if (isRecord(classicReport)) {
    return classicReport
  }

  return payload
}

export const normalizeCspReportPayload = (
  rawBody: string,
  context: CspReportContext
): CspReportNormalizationResult => {
  if (Buffer.byteLength(rawBody, 'utf8') > maxCspReportBodyBytes) {
    return { accepted: false, reason: 'body too large' }
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(rawBody)
  } catch {
    return { accepted: false, reason: 'invalid json' }
  }

  const report = firstReport(parsed)
  if (!report) {
    return { accepted: false, reason: 'missing report' }
  }

  const violatedDirective = stringValue(report['violated-directive']) ?? stringValue(report.violatedDirective)
  const effectiveDirective = stringValue(report['effective-directive']) ?? stringValue(report.effectiveDirective)
  if (!violatedDirective && !effectiveDirective) {
    return { accepted: false, reason: 'missing directive' }
  }

  return {
    accepted: true,
    report: {
      requestId: context.requestId,
      documentUriOrigin: originOnly(report['document-uri'] ?? report.documentURI ?? report.url),
      blockedUriOrigin: originOnly(report['blocked-uri'] ?? report.blockedURL),
      violatedDirective: violatedDirective ?? effectiveDirective ?? 'unknown',
      effectiveDirective: effectiveDirective ?? violatedDirective ?? 'unknown',
      disposition: dispositionFor(report.disposition),
      userAgentHash: userAgentHash(context.userAgent)
    }
  }
}
