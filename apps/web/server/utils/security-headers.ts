export interface HtmlSecurityHeaderOptions {
  scriptNonce?: string
  connectSources?: string[]
}

const scriptSource = (scriptNonce?: string): string => {
  if (scriptNonce && scriptNonce.trim().length > 0) {
    return `script-src 'self' 'nonce-${scriptNonce}'`
  }

  return "script-src 'self'"
}

export const htmlSecurityHeaders = (options: HtmlSecurityHeaderOptions = {}): Record<string, string> => ({
  'Content-Security-Policy': [
    "default-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "img-src 'self' data: blob:",
    "style-src 'self' 'unsafe-inline'",
    scriptSource(options.scriptNonce),
    connectSource(options.connectSources)
  ].join('; '),
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(self), geolocation=()'
})

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
