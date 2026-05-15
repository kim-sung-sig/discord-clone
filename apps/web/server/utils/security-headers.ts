export interface HtmlSecurityHeaderOptions {
  scriptNonce?: string
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
    "connect-src 'self' http://127.0.0.1:* http://localhost:* ws://127.0.0.1:* ws://localhost:*"
  ].join('; '),
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
})

export const addNonceToScriptTags = (htmlParts: string[], scriptNonce: string): string[] =>
  htmlParts.map((part) => part.replace(/<script(?![^>]*\bnonce=)/g, `<script nonce="${scriptNonce}"`))
