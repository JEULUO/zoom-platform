import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

interface SystemStatusResponse {
  service: string
  status: 'UP'
  timestamp: string
}

export const useSystemStore = defineStore('system', () => {
  const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const service = ref('zoom-platform-server')
  const checkedAt = ref<string | null>(null)

  const isReady = computed(() => status.value === 'ready')

  async function checkStatus() {
    status.value = 'loading'

    try {
      const response = await fetch('/api/v1/system/status', {
        headers: { Accept: 'application/json' },
      })

      if (!response.ok) {
        throw new Error(`Status request failed: ${response.status}`)
      }

      const result = (await response.json()) as SystemStatusResponse
      service.value = result.service
      checkedAt.value = result.timestamp
      status.value = result.status === 'UP' ? 'ready' : 'error'
    } catch {
      checkedAt.value = new Date().toISOString()
      status.value = 'error'
    }
  }

  return { checkedAt, checkStatus, isReady, service, status }
})
