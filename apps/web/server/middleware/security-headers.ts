import { defineEventHandler, setResponseHeaders } from 'h3'
import { useRuntimeConfig } from '#imports'
import { connectSourcesFromUrls, htmlSecurityHeaders } from '../utils/security-headers'

export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event)
  setResponseHeaders(event, htmlSecurityHeaders({
    connectSources: connectSourcesFromUrls([config.public.apiBaseUrl])
  }))
})
