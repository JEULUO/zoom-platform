import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { useAuthStore } from './auth'

export type UserStatus = 'PENDING' | 'ACTIVE' | 'LOCKED' | 'DISABLED'
export type DataScope = 'ALL' | 'ASSIGNED_CAMPUSES' | 'SELF'

export interface UserRoleAssignment {
  code: string
  name: string
  dataScope: DataScope
}

export interface UserRoleOption extends UserRoleAssignment {
  id: number
  sortOrder: number
}

export interface UserCampusAssignment {
  id: number
  code: string
  name: string
  primaryCampus: boolean
  status: 'ACTIVE' | 'INACTIVE'
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
  email: string | null
  phone: string | null
  status: UserStatus
  roles: UserRoleAssignment[]
  campuses: UserCampusAssignment[]
  lastLoginAt: string | null
  version: number
  updatedAt: string
}

export interface UserDetail extends UserSummary {
  preferredLanguage: string
  timezone: string
  failedLoginAttempts: number
  lockedUntil: string | null
  passwordChangedAt: string | null
  createdAt: string
}

interface UserPageResponse {
  items: UserSummary[]
  page: number
  pageSize: number
  total: number
}

interface UserDirectoryOptions {
  statuses: UserStatus[]
  campuses: UserCampusAssignment[]
  roles: UserRoleOption[]
}

export interface UserFormValues {
  username: string
  initialPassword: string
  displayName: string
  email: string
  phone: string
  preferredLanguage: string
  timezone: string
  status: 'PENDING' | 'ACTIVE'
  roleIds: number[]
  campusIds: number[]
  primaryCampusId: number | null
}

interface ApiErrorResponse {
  code?: string
  message?: string
}

export class UserDirectoryRequestError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'UserDirectoryRequestError'
  }
}

export const useUserDirectoryStore = defineStore('user-directory', () => {
  const authStore = useAuthStore()
  const items = ref<UserSummary[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const statusFilter = ref<UserStatus | ''>('')
  const campusFilter = ref<number | ''>('')
  const statuses = ref<UserStatus[]>([])
  const campuses = ref<UserCampusAssignment[]>([])
  const roles = ref<UserRoleOption[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

  async function readError(response: Response): Promise<never> {
    let result: ApiErrorResponse = {}
    try {
      result = (await response.json()) as ApiErrorResponse
    } catch {
      // Keep a stable fallback for proxy and unexpected server responses.
    }
    throw new UserDirectoryRequestError(
      result.code ?? 'USER_DIRECTORY_REQUEST_FAILED',
      response.status,
      result.message ?? `User directory request failed: ${response.status}`,
    )
  }

  async function request<T>(path: string, init: RequestInit = {}) {
    const response = await authStore.authorizedFetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
    if (!response.ok) await readError(response)
    return (await response.json()) as T
  }

  async function fetchPage(requestedPage = page.value) {
    loading.value = true
    error.value = null
    const params = new URLSearchParams({
      page: String(requestedPage),
      pageSize: String(pageSize.value),
    })
    if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
    if (statusFilter.value) params.set('status', statusFilter.value)
    if (campusFilter.value !== '') params.set('campusId', String(campusFilter.value))

    try {
      const result = await request<UserPageResponse>(`/api/v1/users?${params}`)
      items.value = result.items
      page.value = result.page
      total.value = result.total
    } catch (requestError) {
      error.value =
        requestError instanceof UserDirectoryRequestError
          ? requestError.code
          : 'USER_DIRECTORY_REQUEST_FAILED'
      throw requestError
    } finally {
      loading.value = false
    }
  }

  async function fetchOptions() {
    const result = await request<UserDirectoryOptions>('/api/v1/users/options')
    statuses.value = result.statuses
    campuses.value = result.campuses
    roles.value = result.roles
  }

  async function fetchById(id: number) {
    return request<UserDetail>(`/api/v1/users/${id}`)
  }

  async function createUser(values: UserFormValues) {
    return request<UserDetail>('/api/v1/users', {
      method: 'POST',
      body: JSON.stringify(values),
    })
  }

  async function updateUser(id: number, values: UserFormValues, version: number) {
    const { username: _username, initialPassword: _initialPassword, status: _status, ...profile } = values
    return request<UserDetail>(`/api/v1/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...profile, version }),
    })
  }

  async function updateUserStatus(id: number, status: 'ACTIVE' | 'DISABLED', version: number) {
    return request<UserDetail>(`/api/v1/users/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, version }),
    })
  }

  return {
    campusFilter,
    campuses,
    createUser,
    error,
    fetchById,
    fetchOptions,
    fetchPage,
    items,
    keyword,
    loading,
    page,
    pageSize,
    roles,
    statusFilter,
    statuses,
    total,
    totalPages,
    updateUser,
    updateUserStatus,
  }
})
