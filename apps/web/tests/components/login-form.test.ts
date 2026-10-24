import { mountSuspended } from '@nuxt/test-utils/runtime'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginForm from '../../components/auth/LoginForm.vue'
import koKr from '../../locales/ko-KR.json'
import { useAuthStore } from '../../stores/auth'
import { usePreferencesStore } from '../../stores/preferences'

const nuxtMocks = vi.hoisted(() => ({
  navigateTo: vi.fn()
}))

vi.mock('#app', async (importOriginal) => {
  const actual = await importOriginal<typeof import('#app')>()
  return {
    ...actual,
    navigateTo: nuxtMocks.navigateTo
  }
})

describe('LoginForm', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    window.localStorage.clear()
    window.sessionStorage.clear()
    nuxtMocks.navigateTo.mockReset()
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
    expect(nuxtMocks.navigateTo).toHaveBeenCalledTimes(1)
    expect(nuxtMocks.navigateTo).toHaveBeenCalledWith('/', { replace: true })
    expect(wrapper.find('[data-testid="login-success"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="open-workspace"]').exists()).toBe(false)

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
    expect(nuxtMocks.navigateTo).not.toHaveBeenCalled()
  })

  it('opens the workspace without a form submit when a refresh cookie restores the session', async () => {
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify({
      accessToken: 'restored-access-token',
      user: { id: 'user-1', username: 'user', displayName: 'User' }
    }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    }))
    await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(useAuthStore().accessToken).toBe('restored-access-token')
    expect(nuxtMocks.navigateTo).toHaveBeenCalledWith('/', { replace: true })
  })

  it('keeps the form usable when navigating after a restored session fails', async () => {
    nuxtMocks.navigateTo.mockRejectedValueOnce(new Error('navigation aborted'))
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify({
      accessToken: 'restored-access-token',
      user: { id: 'user-1', username: 'user', displayName: 'User' }
    }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    }))
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(nuxtMocks.navigateTo).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('stays on the form when there is no session to restore', async () => {
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify({ message: 'refresh token invalid' }), {
      status: 401,
      headers: { 'content-type': 'application/json' }
    }))
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(nuxtMocks.navigateTo).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="login-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('shows no developer copy and renders the form text from locale keys', async () => {
    vi.stubGlobal('fetch', async () => new Response(null, { status: 401 }))
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Sign in to Discord Clone')
    expect(text).not.toMatch(/Spring Boot|httpOnly|access token/i)
    expect(wrapper.find('[data-testid="login-token-policy"]').exists()).toBe(false)
  })

  it('renders the login form and its errors in Korean when the locale is ko-KR', async () => {
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify({ message: 'invalid credentials' }), {
      status: 401,
      headers: { 'content-type': 'application/json' }
    }))
    usePreferencesStore().setLocale('ko-KR')
    const wrapper = await mountSuspended(LoginForm, {
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(wrapper.text()).toContain(koKr['login.title'])
    expect(wrapper.get('label[for="login-email"]').text()).toContain(koKr['login.email'])
    expect(wrapper.get('label[for="login-password"]').text()).toContain(koKr['login.password'])
    expect(wrapper.get('[data-testid="login-submit"]').text()).toBe(koKr['login.submit'])

    await wrapper.get('[data-testid="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="login-password"]').setValue('wrong-password')
    await wrapper.get('[data-testid="login-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="login-error"]').text()).toBe(koKr['login.error.invalidCredentials'])
  })
})
