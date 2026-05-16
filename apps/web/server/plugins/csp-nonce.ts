import { randomBytes } from 'node:crypto'
import { defineNitroPlugin } from 'nitropack/runtime'
import { setResponseHeaders } from 'h3'
import { useRuntimeConfig } from '#imports'
import { addNonceToScriptTags, connectSourcesFromUrls, htmlSecurityHeaders } from '../utils/security-headers'

const createScriptNonce = (): string => randomBytes(18).toString('base64url')

export default defineNitroPlugin((nitroApp) => {
  nitroApp.hooks.hook('render:html', (html, context) => {
    const scriptNonce = createScriptNonce()

    html.head = addNonceToScriptTags(html.head, scriptNonce)
    html.body = addNonceToScriptTags(html.body, scriptNonce)
    html.bodyAppend = addNonceToScriptTags(html.bodyAppend, scriptNonce)

    const config = useRuntimeConfig(context.event)
    setResponseHeaders(context.event, htmlSecurityHeaders({
      scriptNonce,
      connectSources: connectSourcesFromUrls([config.public.apiBaseUrl])
    }))
  })
})
