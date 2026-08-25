import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { useAuthStore } from './auth'

export type CampusStatus = 'ACTIVE' | 'INACTIVE'

export interface CampusSummary {
  id: number
  code: string
  name: string
  city: string | null
  timezone: string
  countryCode: string
  status: CampusStatus
  sortOrder: number
  version: number
  updatedAt: string
}

export interface CampusDetail extends CampusSummary {
  legalName: string | null
  addressLine1: string | null
  addressLine2: string | null
  postalCode: string | null
  contactEmail: string | null
  contactPhone: string | null
  createdAt: string
}

export interface CampusFormValues {
  code: string
  name: string
  legalName: string
  timezone: string
  countryCode: string
  addressLine1: string
  addressLine2: string
  city: string
  postalCode: string
  contactEmail: string
  contactPhone: string
  sortOrder: number
}

interface CampusPageResponse {
  items: CampusSummary[]
  page: number
  pageSize: number
  total: number
}

interface ApiErrorResponse {
  code?: string
  message?: string
}

export class CampusRequestError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'CampusRequestError'
  }
}

export const useCampusStore = defineStore('campus', () => {
  const authStore = useAuthStore()
  const items = ref<CampusSummary[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const keyword = ref('')
  const statusFilter = ref<CampusStatus | ''>('')
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
    throw new CampusRequestError(
      result.code ?? 'CAMPUS_REQUEST_FAILED',
      response.status,
      result.message ?? `Campus request failed: ${response.status}`,
    )
  }

  async function request<T>(path: string, init: RequestInit = {}) {
    const response = await authStore.authorizedFetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
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

    try {
      const result = await request<CampusPageResponse>(`/api/v1/campuses?${params}`)
      items.value = result.items
      page.value = result.page
      total.value = result.total
    } catch (requestError) {
      error.value =
        requestError instanceof CampusRequestError
          ? requestError.code
          : 'CAMPUS_REQUEST_FAILED'
      throw requestError
    } finally {
      loading.value = false
    }
  }

  async function fetchById(id: number) {
    return request<CampusDetail>(`/api/v1/campuses/${id}`)
  }

  async function createCampus(values: CampusFormValues) {
    return request<CampusDetail>('/api/v1/campuses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values),
    })
  }

  async function updateCampus(id: number, values: CampusFormValues, version: number) {
    return request<CampusDetail>(`/api/v1/campuses/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...values, code: undefined, version }),
    })
  }

  async function updateCampusStatus(id: number, status: CampusStatus, version: number) {
    return request<CampusDetail>(`/api/v1/campuses/${id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, version }),
    })
  }

  return {
    createCampus,
    error,
    fetchById,
    fetchPage,
    items,
    keyword,
    loading,
    page,
    pageSize,
    statusFilter,
    total,
    totalPages,
    updateCampus,
    updateCampusStatus,
  }
})
