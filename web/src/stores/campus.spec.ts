import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { CampusRequestError, useCampusStore } from './campus'

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

describe('campus store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('loads a filtered page and updates pagination state', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        items: [
          {
            id: 1,
            code: 'LON',
            name: 'London Campus',
            city: 'London',
            timezone: 'Europe/London',
            countryCode: 'GB',
            status: 'ACTIVE',
            sortOrder: 10,
            version: 2,
            updatedAt: '2026-08-25T09:00:00Z',
          },
        ],
        page: 2,
        pageSize: 20,
        total: 45,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const store = useCampusStore()
    store.keyword = ' London '
    store.statusFilter = 'ACTIVE'
    await store.fetchPage(2)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/campuses?page=2&pageSize=20&keyword=London&status=ACTIVE',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(store.items[0]?.code).toBe('LON')
    expect(store.page).toBe(2)
    expect(store.total).toBe(45)
    expect(store.totalPages).toBe(3)
    expect(store.loading).toBe(false)
  })

  it('sends create, update and status payloads with the expected contracts', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }))
    vi.stubGlobal('fetch', fetchMock)
    const store = useCampusStore()
    const values = {
      code: 'SHA',
      name: 'Shanghai Campus',
      legalName: '',
      timezone: 'Asia/Shanghai',
      countryCode: 'CN',
      addressLine1: '',
      addressLine2: '',
      city: 'Shanghai',
      postalCode: '',
      contactEmail: '',
      contactPhone: '',
      sortOrder: 20,
    }

    await store.createCampus(values)
    await store.updateCampus(1, values, 3)
    await store.updateCampusStatus(1, 'INACTIVE', 4)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/campuses',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(values) }),
    )
    expect(JSON.parse(fetchMock.mock.calls[1]?.[1]?.body as string)).toEqual({
      ...values,
      code: undefined,
      version: 3,
    })
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/campuses/1/status',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ status: 'INACTIVE', version: 4 }),
      }),
    )
  })

  it('keeps a stable API error code when page loading fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'INVALID_TIMEZONE', message: 'Timezone is invalid' }, 400),
      ),
    )

    const store = useCampusStore()
    await expect(store.fetchPage()).rejects.toBeInstanceOf(CampusRequestError)
    expect(store.error).toBe('INVALID_TIMEZONE')
    expect(store.loading).toBe(false)
  })
})
