import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { UserDirectoryRequestError, useUserDirectoryStore } from './user-directory'

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

describe('user directory store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('loads a page with keyword, status and campus filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        items: [
          {
            id: 2,
            username: 'alice',
            displayName: 'Alice Chen',
            email: 'alice@example.com',
            phone: null,
            status: 'ACTIVE',
            roles: [],
            campuses: [],
            lastLoginAt: null,
            version: 0,
            updatedAt: '2026-08-25T08:00:00Z',
          },
        ],
        page: 2,
        pageSize: 20,
        total: 22,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const store = useUserDirectoryStore()
    store.keyword = ' Alice '
    store.statusFilter = 'ACTIVE'
    store.campusFilter = 4
    await store.fetchPage(2)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/users?page=2&pageSize=20&keyword=Alice&status=ACTIVE&campusId=4',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(store.items[0]?.username).toBe('alice')
    expect(store.page).toBe(2)
    expect(store.totalPages).toBe(2)
  })

  it('loads filter options and a user detail', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          statuses: ['PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'],
          campuses: [
            {
              id: 1,
              code: 'RICHMOND',
              name: 'Richmond',
              primaryCampus: false,
              status: 'ACTIVE',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ id: 2, username: 'alice' }))
    vi.stubGlobal('fetch', fetchMock)

    const store = useUserDirectoryStore()
    await store.fetchOptions()
    const detail = await store.fetchById(2)

    expect(store.statuses).toEqual(['PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'])
    expect(store.campuses[0]?.code).toBe('RICHMOND')
    expect(detail.username).toBe('alice')
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/users/2',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('keeps the API error code when page loading fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'USER_NOT_FOUND', message: 'User was not found' }, 404),
      ),
    )

    const store = useUserDirectoryStore()
    await expect(store.fetchPage()).rejects.toBeInstanceOf(UserDirectoryRequestError)
    expect(store.error).toBe('USER_NOT_FOUND')
    expect(store.loading).toBe(false)
  })
})
