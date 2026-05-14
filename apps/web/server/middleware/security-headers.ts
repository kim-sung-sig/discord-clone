import { defineEventHandler, setResponseHeaders } from 'h3'
import { htmlSecurityHeaders } from '../utils/security-headers'

export default defineEventHandler((event) => {
  setResponseHeaders(event, htmlSecurityHeaders())
})
