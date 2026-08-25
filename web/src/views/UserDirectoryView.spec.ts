import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import zhCN from '@/i18n/messages/zh-CN'
import { useAuthStore, type AuthenticatedUser } from '@/stores/auth'
import {
  useUserDirectoryStore,
  type UserDetail,
  type UserSummary,
} from '@/stores/user-directory'
import UserDirectoryView from './UserDirectoryView.vue'

vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))

const summary: UserSummary = {
  id: 2,
  username: 'alice',
  displayName: 'Alice Chen',
  email: 'alice@example.com',
  phone: null,
  status: 'ACTIVE',
  roles: [{ code: 'CAMPUS_ADMIN', name: 'Campus Administrator', dataScope: 'ASSIGNED_CAMPUSES' }],
  campuses: [
    {
      id: 1,
      code: 'RICHMOND',
      name: 'Richmond',
      primaryCampus: true,
      status: 'ACTIVE',
    },
  ],
  lastLoginAt: '2026-08-25T08:00:00Z',
  version: 1,
  updatedAt: '2026-08-25T08:00:00Z',
}

const detail: UserDetail = {
  ...summary,
  preferredLanguage: 'zh-CN',
  timezone: 'Asia/Shanghai',
  failedLoginAttempts: 0,
  lockedUntil: null,
  passwordChangedAt: '2026-08-20T08:00:00Z',
  createdAt: '2026-08-01T08:00:00Z',
}

function user(dataScope: AuthenticatedUser['dataScope'] = 'ALL'): AuthenticatedUser {
  return {
    id: 1,
    username: 'admin',
    displayName: 'System Administrator',
    preferredLanguage: 'zh-CN',
    timezone: 'Asia/Shanghai',
    dataScope,
    roles: ['SUPER_ADMIN'],
    permissions: ['user.read'],
    campusIds: dataScope === 'ALL' ? [] : [1],
  }
}

function mountDirectory(dataScope: AuthenticatedUser['dataScope'] = 'ALL') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore()
  authStore.$patch({ accessToken: 'access-token', status: 'authenticated', user: user(dataScope) })
  const directoryStore = useUserDirectoryStore()
  directoryStore.$patch({
    items: [summary],
    total: 1,
    statuses: ['PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'],
    campuses: summary.campuses,
  })
  vi.spyOn(directoryStore, 'fetchPage').mockResolvedValue(undefined)
  vi.spyOn(directoryStore, 'fetchOptions').mockResolvedValue(undefined)

  const wrapper = mount(UserDirectoryView, {
    global: {
      plugins: [
        pinia,
        createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } }),
      ],
      stubs: {
        AppShell: { template: '<div><slot /></div>' },
        ElTooltip: { template: '<span><slot /></span>' },
      },
    },
  })
  return { directoryStore, wrapper }
}

describe('user directory view', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('searches and clears all directory filters', async () => {
    const { directoryStore, wrapper } = mountDirectory()
    await flushPromises()

    await wrapper.get('.directory-search input').setValue('Alice')
    await wrapper.get('.directory-search').trigger('submit')
    expect(directoryStore.fetchPage).toHaveBeenLastCalledWith(1)
    expect(directoryStore.keyword).toBe('Alice')

    directoryStore.statusFilter = 'ACTIVE'
    directoryStore.campusFilter = 1
    await wrapper.get('.directory-clear').trigger('click')
    await flushPromises()

    expect(directoryStore.keyword).toBe('')
    expect(directoryStore.statusFilter).toBe('')
    expect(directoryStore.campusFilter).toBe('')
  })

  it('loads and displays the selected user detail', async () => {
    const { directoryStore, wrapper } = mountDirectory()
    vi.spyOn(directoryStore, 'fetchById').mockResolvedValue(detail)

    await wrapper.get('button[aria-label="查看 Alice Chen"]').trigger('click')
    await flushPromises()

    expect(directoryStore.fetchById).toHaveBeenCalledWith(2)
    expect(wrapper.get('[role="dialog"]').text()).toContain('Campus Administrator')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Richmond')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Asia/Shanghai')
  })

  it('shows the assigned-campus scope without exposing write actions', () => {
    const { wrapper } = mountDirectory('ASSIGNED_CAMPUSES')

    expect(wrapper.text()).toContain('当前数据范围：已分配校区')
    expect(wrapper.find('.primary-command').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('新建用户')
  })
})
