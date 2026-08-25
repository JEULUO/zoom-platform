import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useSystemStore } from './system'

describe('system store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('marks the backend as ready when the status endpoint is up', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            service: 'zoom-platform-server',
            status: 'UP',
            timestamp: '2026-08-25T00:00:00Z',
          }),
      }),
    )

    const store = useSystemStore()
    await store.checkStatus()

    expect(store.status).toBe('ready')
    expect(store.isReady).toBe(true)
    expect(store.checkedAt).toBe('2026-08-25T00:00:00Z')
  })

  it('reports an unavailable backend when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    const store = useSystemStore()
    await store.checkStatus()

    expect(store.status).toBe('error')
    expect(store.isReady).toBe(false)
  })
})
