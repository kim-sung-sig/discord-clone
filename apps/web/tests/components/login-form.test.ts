import { mountSuspended } from '@nuxt/test-utils/runtime'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import LoginForm from '../../components/auth/LoginForm.vue'
import { useAuthStore } from '../../stores/auth'

describe('LoginForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('stores an in-memory placeholder access token after email/password submission', async () => {
    const wrapper = await mountSuspended(LoginForm)

    await wrapper.get('[data-testid="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="login-password"]').setValue('correct-password')
    await wrapper.get('[data-testid="login-form"]').trigger('submit')

    const auth = useAuthStore()

    expect(auth.accessToken).toBe('local-placeholder-access-token')
    expect(wrapper.get('[data-testid="login-success"]').text()).toContain('Signed in locally')
  })

  it('renders a clear error when login fails', async () => {
    const wrapper = await mountSuspended(LoginForm)

    await wrapper.get('[data-testid="login-email"]').setValue('user@example.com')
    await wrapper.get('[data-testid="login-password"]').setValue('wrong-password')
    await wrapper.get('[data-testid="login-form"]').trigger('submit')

    expect(wrapper.get('[data-testid="login-error"]').text()).toContain(
      'Unable to sign in with those credentials'
    )
  })
})
