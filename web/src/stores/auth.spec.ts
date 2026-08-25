import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from './auth'

const user = {
  id: 1,
  username: 'admin',
  displayName: 'System Administrator',
  preferredLanguage: 'zh-CN',
  timezone: 'Asia/Shanghai',
  dataScope: 'ALL' as const,
  roles: ['SUPER_ADMIN'],
  permissions: ['platform.manage'],
  campusIds: [],
}

function session(accessToken = 'access-token') {
  return {
    tokenType: 'Bearer',
    accessToken,
    expiresIn: 900,
    user,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('logs in and keeps the access token in application state', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(session()))
    vi.stubGlobal('fetch', fetchMock)

    const store = useAuthStore()
    await store.login('admin', 'ZoomDev@2026!')

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.roles).toContain('SUPER_ADMIN')
    expect(store.hasPermission('platform.manage')).toBe(true)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })

  it('restores a session once from the refresh cookie', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(session('restored-token')))
    vi.stubGlobal('fetch', fetchMock)

    const store = useAuthStore()
    await Promise.all([store.initialize(), store.initialize()])

    expect(store.accessToken).toBe('restored-token')
    expect(store.status).toBe('authenticated')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('makes concurrent initializers wait for the same refresh result', async () => {
    let finishRefresh: ((value: ReturnType<typeof jsonResponse>) => void) | undefined
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(
        new Promise((resolve) => {
          finishRefresh = resolve
        }),
      ),
    )

    const store = useAuthStore()
    const first = store.initialize()
    const second = store.initialize()
    let secondFinished = false
    void second.then(() => {
      secondFinished = true
    })

    await Promise.resolve()
    expect(secondFinished).toBe(false)

    finishRefresh?.(jsonResponse(session('concurrent-token')))
    await Promise.all([first, second])
    expect(store.accessToken).toBe('concurrent-token')
  })

  it('becomes anonymous when no refresh session exists', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'INVALID_REFRESH_TOKEN', message: 'Refresh session is invalid' }, 401),
      ),
    )

    const store = useAuthStore()
    await store.initialize()

    expect(store.isAuthenticated).toBe(false)
    expect(store.status).toBe('anonymous')
  })

  it('surfaces login errors and clears partial session state', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'ACCOUNT_LOCKED', message: 'Account is locked' }, 423),
      ),
    )

    const store = useAuthStore()
    await expect(store.login('admin', 'bad-password')).rejects.toMatchObject({
      code: 'ACCOUNT_LOCKED',
      status: 423,
    })
    expect(store.status).toBe('anonymous')
  })

  it('sends the bearer token on logout and always clears local state', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(session()))
      .mockResolvedValueOnce(jsonResponse(null, 204))
    vi.stubGlobal('fetch', fetchMock)

    const store = useAuthStore()
    await store.login('admin', 'ZoomDev@2026!')
    await store.logout()

    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer access-token' }),
      }),
    )
    expect(store.status).toBe('anonymous')
    expect(store.accessToken).toBeNull()
  })
})
