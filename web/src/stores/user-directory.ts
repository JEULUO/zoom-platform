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

  async function request<T>(path: string) {
    const response = await authStore.authorizedFetch(path, {
      headers: { Accept: 'application/json' },
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
  }

  async function fetchById(id: number) {
    return request<UserDetail>(`/api/v1/users/${id}`)
  }

  return {
    campusFilter,
    campuses,
    error,
    fetchById,
    fetchOptions,
    fetchPage,
    items,
    keyword,
    loading,
    page,
    pageSize,
    statusFilter,
    statuses,
    total,
    totalPages,
  }
})
