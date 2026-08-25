import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import zhCN from '@/i18n/messages/zh-CN'
import LoginView from './LoginView.vue'

const replace = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/operations' } }),
  useRouter: () => ({ replace }),
}))

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [
        createPinia(),
        createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } }),
      ],
    },
  })
}

function response(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  }
}

describe('login view', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    replace.mockReset()
    replace.mockResolvedValue(undefined)
  })

  it('submits credentials and follows the protected-route redirect', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      response({
        tokenType: 'Bearer',
        accessToken: 'access-token',
        expiresIn: 900,
        user: {
          id: 1,
          username: 'admin',
          displayName: 'System Administrator',
          preferredLanguage: 'zh-CN',
          timezone: 'Asia/Shanghai',
          dataScope: 'ALL',
          roles: ['SUPER_ADMIN'],
          permissions: ['platform.manage'],
          campusIds: [],
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountLogin()
    await wrapper.get('input[name="username"]').setValue('admin')
    await wrapper.get('input[name="password"]').setValue('ZoomDev@2026!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(replace).toHaveBeenCalledWith('/operations')
  })

  it('shows the account-lock message returned by the API', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response({ code: 'ACCOUNT_LOCKED', message: 'Account is temporarily locked' }, 423),
      ),
    )

    const wrapper = mountLogin()
    await wrapper.get('input[name="username"]').setValue('admin')
    await wrapper.get('input[name="password"]').setValue('bad-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('账户已暂时锁定')
    expect(replace).not.toHaveBeenCalled()
  })
})
