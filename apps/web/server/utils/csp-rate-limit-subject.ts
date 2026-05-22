import { createHash } from 'node:crypto'
import { isIP } from 'node:net'

export interface CspRateLimitSubjectInput {
  forwardedFor?: string
  realIp?: string
  remoteAddress?: string
  trustedProxyCidrs?: string[]
}

export type CspRateLimitSubjectSource = 'remote-address' | 'x-forwarded-for' | 'x-real-ip' | 'unknown'

export interface CspRateLimitSubjectDiagnostics {
  source: CspRateLimitSubjectSource
  subjectHashPrefix: string
  trustedProxyConfigured: boolean
  trustedProxyMatched: boolean
  trustedProxyRuleCount: number
  forwardedForPresent: boolean
  realIpPresent: boolean
}

interface CspRateLimitSubjectResolution {
  subject: string
  diagnostics: CspRateLimitSubjectDiagnostics
}

export const rateLimitSubjectFor = (input: CspRateLimitSubjectInput): string =>
  resolveRateLimitSubject(input).subject

export const cspRateLimitSubjectDiagnosticsFor = (input: CspRateLimitSubjectInput): CspRateLimitSubjectDiagnostics =>
  resolveRateLimitSubject(input).diagnostics

const resolveRateLimitSubject = ({
  forwardedFor,
  realIp,
  remoteAddress,
  trustedProxyCidrs = []
}: CspRateLimitSubjectInput): CspRateLimitSubjectResolution => {
  const remoteIp = normalizeIpAddress(remoteAddress)
  const fallbackSubject = remoteIp ?? 'unknown'
  const trustedProxyMatched = Boolean(remoteIp && isTrustedProxy(remoteIp, trustedProxyCidrs))
  let subject = fallbackSubject
  let source: CspRateLimitSubjectSource = remoteIp ? 'remote-address' : 'unknown'

  if (remoteIp && trustedProxyMatched) {
    const forwardedIp = firstForwardedIp(forwardedFor)
    const normalizedRealIp = normalizeIpAddress(realIp)
    if (forwardedIp) {
      subject = forwardedIp
      source = 'x-forwarded-for'
    } else if (normalizedRealIp) {
      subject = normalizedRealIp
      source = 'x-real-ip'
    }
  }

  return {
    subject,
    diagnostics: {
      source,
      subjectHashPrefix: hashSubject(subject).slice(0, 12),
      trustedProxyConfigured: trustedProxyCidrs.length > 0,
      trustedProxyMatched,
      trustedProxyRuleCount: trustedProxyCidrs.length,
      forwardedForPresent: Boolean(forwardedFor?.trim()),
      realIpPresent: Boolean(realIp?.trim())
    }
  }
}

export const createTrustedProxyCidrs = (env: Record<string, string | undefined> = process.env): string[] =>
  (env.NUXT_CSP_RATE_LIMIT_TRUSTED_PROXY_CIDRS ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .filter(isValidTrustedProxyRule)

const firstForwardedIp = (forwardedFor?: string): string | undefined =>
  forwardedFor
    ?.split(',')
    .map(normalizeIpAddress)
    .find((entry): entry is string => Boolean(entry))

const normalizeIpAddress = (value?: string): string | undefined => {
  const trimmed = value?.trim()
  if (!trimmed) {
    return undefined
  }

  const withoutBrackets = trimmed.startsWith('[') && trimmed.includes(']')
    ? trimmed.slice(1, trimmed.indexOf(']'))
    : trimmed
  const candidate = normalizeIpv4WithOptionalPort(withoutBrackets)
  const ipv4Mapped = candidate.match(/^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i)
  if (ipv4Mapped && isIP(ipv4Mapped[1]) === 4) {
    return ipv4Mapped[1]
  }
  return isIP(candidate) ? candidate.toLowerCase() : undefined
}

const normalizeIpv4WithOptionalPort = (value: string): string => {
  const colonCount = (value.match(/:/g) ?? []).length
  if (colonCount === 1) {
    const [host] = value.split(':')
    if (isIP(host) === 4) {
      return host
    }
  }
  return value
}

const isValidTrustedProxyRule = (entry: string): boolean => {
  if (normalizeIpAddress(entry)) {
    return true
  }
  const cidr = parseIpv4Cidr(entry)
  return Boolean(cidr)
}

const isTrustedProxy = (remoteIp: string, trustedProxyCidrs: string[]): boolean =>
  trustedProxyCidrs.some((entry) => {
    const exactIp = normalizeIpAddress(entry)
    if (exactIp) {
      return exactIp === remoteIp
    }

    const cidr = parseIpv4Cidr(entry)
    return Boolean(cidr) && isIpv4InCidr(remoteIp, cidr)
  })

const parseIpv4Cidr = (entry: string): { network: number, prefixLength: number } | undefined => {
  const [network, prefix] = entry.split('/')
  const prefixLength = Number(prefix)
  const networkIp = normalizeIpAddress(network)
  if (isIP(networkIp ?? '') !== 4 || !Number.isInteger(prefixLength) || prefixLength < 0 || prefixLength > 32) {
    return undefined
  }

  return {
    network: ipv4ToNumber(networkIp!),
    prefixLength
  }
}

const isIpv4InCidr = (ip: string, cidr: { network: number, prefixLength: number }): boolean => {
  if (isIP(ip) !== 4) {
    return false
  }

  const mask = cidr.prefixLength === 0
    ? 0
    : (0xffffffff << (32 - cidr.prefixLength)) >>> 0
  return (ipv4ToNumber(ip) & mask) === (cidr.network & mask)
}

const ipv4ToNumber = (ip: string): number =>
  ip.split('.').reduce((value, octet) => ((value << 8) + Number(octet)) >>> 0, 0)

const hashSubject = (subject: string): string =>
  createHash('sha256')
    .update(subject.trim() || 'unknown')
    .digest('hex')
