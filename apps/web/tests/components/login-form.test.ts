import { mountSuspended } from '@nuxt/test-utils/runtime'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginForm from '../../components/auth/LoginForm.vue'
import { useAuthStore } from '../../stores/auth'

describe('LoginForm', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('logs in through the backend API with cookie refresh and memory-only access token', async () => {
    const calls: Array<{ input: RequestInfo | URL, init?: RequestInit }> = []
    const fetcher: typeof fetch = async (input, init) => {
      calls.push({ input, init })
      if (String(input).endsWith('/api/auth/refresh')) {
        return new Response(JSON.stringify({ message: 'refresh token invalid' }), {
          status: 401,
          headers: { 'content-type': 'application/json' }
        })
      }
      return new Response(JSON.stringify({
        accessToken: 'backend-access-token',
        user: {
          id: 'user-1',
          username: 'user',
          displayName: 'User'
        }
      }), {
        status: 200,
        headers: { 'content-type': 'application/json' }
      })
    }
    vi.stubGlobal('fetch', fetcher)
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })

    await wrapper.get('[data-testid="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="login-password"]').setValue('correct horse battery staple')
    await wrapper.get('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    const auth = useAuthStore()
    const loginCall = calls.find((call) => String(call.input).endsWith('/api/auth/login'))

    expect(String(loginCall?.input)).toMatch(/\/api\/auth\/login$/)
    expect(loginCall?.init?.method).toBe('POST')
    expect(loginCall?.init?.credentials).toBe('include')
    expect(loginCall?.init?.body).toBe(JSON.stringify({
      email: 'user@example.com',
      password: 'correct horse battery staple'
    }))
    expect(auth.accessToken).toBe('backend-access-token')
    expect(auth.user?.username).toBe('user')
    expect(wrapper.get('[data-testid="login-success"]').text()).toContain('Signed in with backend session')

    expect(JSON.stringify(window.localStorage)).not.toContain('backend-access-token')
    expect(JSON.stringify(window.sessionStorage)).not.toContain('backend-access-token')
    expect(document.cookie).not.toContain('backend-access-token')
  })

  it('renders a clear accessible error when backend login fails', async () => {
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify({ message: 'invalid credentials' }), {
      status: 401,
      headers: { 'content-type': 'application/json' }
    }))
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })

    await wrapper.get('[data-testid="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="login-password"]').setValue('wrong-password')
    await wrapper.get('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="login-error"]').attributes('role')).toBe('alert')
    expect(wrapper.get('[data-testid="login-error"]').text()).toContain(
      'Unable to sign in with those credentials'
    )
  })
})
