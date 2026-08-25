import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export interface AuthenticatedUser {
  id: number
  username: string
  displayName: string
  preferredLanguage: string
  timezone: string
  dataScope: 'ALL' | 'ASSIGNED_CAMPUSES' | 'SELF'
  roles: string[]
  permissions: string[]
  campusIds: number[]
}

interface AccessTokenResponse {
  tokenType: 'Bearer'
  accessToken: string
  expiresIn: number
  user: AuthenticatedUser
}

interface ApiErrorResponse {
  code?: string
  message?: string
}

export class AuthRequestError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'AuthRequestError'
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<AuthenticatedUser | null>(null)
  const status = ref<'idle' | 'loading' | 'authenticated' | 'anonymous'>('idle')
  let initialization: Promise<void> | null = null
  let refreshRequest: Promise<boolean> | null = null

  const isAuthenticated = computed(
    () => status.value === 'authenticated' && accessToken.value !== null && user.value !== null,
  )

  function applySession(session: AccessTokenResponse) {
    accessToken.value = session.accessToken
    user.value = session.user
    status.value = 'authenticated'
  }

  function clearSession() {
    accessToken.value = null
    user.value = null
    status.value = 'anonymous'
  }

  async function readError(response: Response) {
    let error: ApiErrorResponse = {}
    try {
      error = (await response.json()) as ApiErrorResponse
    } catch {
      // The fallback below keeps proxy and network error responses user-friendly.
    }
    throw new AuthRequestError(
      error.code ?? 'AUTH_REQUEST_FAILED',
      response.status,
      error.message ?? `Authentication request failed: ${response.status}`,
    )
  }

  async function requestSession(path: string, init?: RequestInit) {
    const response = await fetch(path, {
      ...init,
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        ...init?.headers,
      },
    })
    if (!response.ok) {
      await readError(response)
    }
    return (await response.json()) as AccessTokenResponse
  }

  async function login(username: string, password: string) {
    status.value = 'loading'
    try {
      const session = await requestSession('/api/v1/auth/login', {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      applySession(session)
    } catch (error) {
      clearSession()
      throw error
    }
  }

  async function refreshSession() {
    if (refreshRequest) return refreshRequest

    refreshRequest = (async () => {
      try {
        applySession(await requestSession('/api/v1/auth/refresh'))
        return true
      } catch {
        clearSession()
        return false
      } finally {
        refreshRequest = null
      }
    })()
    return refreshRequest
  }

  async function initialize() {
    if (initialization) return initialization
    if (status.value !== 'idle') return

    status.value = 'loading'
    initialization = refreshSession().then(() => undefined)
    try {
      await initialization
    } finally {
      initialization = null
    }
  }

  async function logout() {
    const token = accessToken.value
    try {
      if (token) {
        await fetch('/api/v1/auth/logout', {
          method: 'POST',
          credentials: 'include',
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
          },
        })
      }
    } finally {
      clearSession()
    }
  }

  async function authorizedFetch(input: RequestInfo | URL, init: RequestInit = {}) {
    const send = () =>
      fetch(input, {
        ...init,
        credentials: 'include',
        headers: {
          ...init.headers,
          ...(accessToken.value ? { Authorization: `Bearer ${accessToken.value}` } : {}),
        },
      })

    let response = await send()
    if (response.status === 401 && (await refreshSession())) {
      response = await send()
    }
    return response
  }

  function hasPermission(permission: string) {
    return user.value?.permissions.includes(permission) ?? false
  }

  return {
    accessToken,
    authorizedFetch,
    hasPermission,
    initialize,
    isAuthenticated,
    login,
    logout,
    refreshSession,
    status,
    user,
  }
})
