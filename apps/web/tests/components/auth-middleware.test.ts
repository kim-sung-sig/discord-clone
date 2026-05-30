import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import authMiddleware from '../../middleware/auth.global'
import { useAuthStore } from '../../stores/auth'

const nuxtMocks = vi.hoisted(() => ({
  navigateTo: vi.fn((path: string, options?: { replace?: boolean }) => ({ path, options }))
}))

vi.mock('#app', async (importOriginal) => {
  const actual = await importOriginal<typeof import('#app')>()
  return {
    ...actual,
    navigateTo: nuxtMocks.navigateTo
  }
})

describe('auth route middleware', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    nuxtMocks.navigateTo.mockClear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('allows the login route without restoring a session', async () => {
    const auth = useAuthStore()
    const restoreSession = vi.spyOn(auth, 'restoreSession')

    const result = await authMiddleware({ path: '/login' } as never, {} as never)

    expect(result).toBeUndefined()
    expect(restoreSession).not.toHaveBeenCalled()
  })

  it('redirects unauthenticated app shell visits to login after refresh fails', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'restoreSession').mockResolvedValue(false)

    const result = await authMiddleware({ path: '/' } as never, {} as never)

    expect(nuxtMocks.navigateTo).toHaveBeenCalledWith('/login', { replace: true })
    expect(result).toEqual({ path: '/login', options: { replace: true } })
  })

  it('allows app shell visits when refresh restores the user session', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'restoreSession').mockResolvedValue(true)

    const result = await authMiddleware({ path: '/' } as never, {} as never)

    expect(result).toBeUndefined()
    expect(nuxtMocks.navigateTo).not.toHaveBeenCalled()
  })
})
