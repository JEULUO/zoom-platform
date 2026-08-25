import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import zhCN from '@/i18n/messages/zh-CN'
import { useAuthStore, type AuthenticatedUser } from '@/stores/auth'
import { useCampusStore, type CampusSummary } from '@/stores/campus'
import CampusView from './CampusView.vue'

vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))

const campus: CampusSummary = {
  id: 7,
  code: 'LON',
  name: '伦敦校区',
  city: 'London',
  timezone: 'Europe/London',
  countryCode: 'GB',
  status: 'ACTIVE',
  sortOrder: 10,
  version: 3,
  updatedAt: '2026-08-25T09:00:00Z',
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
    permissions: ['campus.read', 'campus.manage'],
    campusIds: dataScope === 'ALL' ? [] : [7],
  }
}

function mountCampus(dataScope: AuthenticatedUser['dataScope'] = 'ALL') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore()
  authStore.$patch({
    accessToken: 'access-token',
    status: 'authenticated',
    user: user(dataScope),
  })
  const campusStore = useCampusStore()
  campusStore.$patch({ items: [campus], total: 1 })
  vi.spyOn(campusStore, 'fetchPage').mockResolvedValue(undefined)

  const wrapper = mount(CampusView, {
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
  return { campusStore, wrapper }
}

describe('campus view', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a campus and reloads the list for an all-scope manager', async () => {
    const { campusStore, wrapper } = mountCampus()
    const createCampus = vi.spyOn(campusStore, 'createCampus').mockResolvedValue({} as never)

    await wrapper.get('.campus-page-heading .primary-command').trigger('click')
    const form = wrapper.get('.campus-form')
    const inputs = form.findAll('input')
    await inputs[0]?.setValue('SHA')
    await inputs[1]?.setValue('上海校区')
    await form.trigger('submit')
    await flushPromises()

    expect(createCampus).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'SHA', name: '上海校区' }),
    )
    expect(campusStore.fetchPage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.campus-modal').exists()).toBe(false)
  })

  it('applies filters and confirms a status change', async () => {
    const { campusStore, wrapper } = mountCampus()
    const updateStatus = vi
      .spyOn(campusStore, 'updateCampusStatus')
      .mockResolvedValue({} as never)

    await wrapper.get('.campus-search input').setValue('London')
    expect(wrapper.get('.campus-search__submit').attributes('type')).toBe('submit')
    await wrapper.get('.campus-search').trigger('submit')
    expect(campusStore.keyword).toBe('London')
    expect(campusStore.fetchPage).toHaveBeenLastCalledWith(1)

    await wrapper.get('button[aria-label="停用 伦敦校区"]').trigger('click')
    await wrapper.get('[role="alertdialog"] .primary-command').trigger('click')
    await flushPromises()

    expect(updateStatus).toHaveBeenCalledWith(7, 'INACTIVE', 3)
    expect(campusStore.fetchPage).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
  })

  it('does not offer campus creation to an assigned-scope manager', () => {
    const { wrapper } = mountCampus('ASSIGNED_CAMPUSES')

    expect(wrapper.find('.campus-page-heading .primary-command').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前数据范围：已分配校区')
    expect(wrapper.find('button[aria-label="编辑 伦敦校区"]').exists()).toBe(true)
  })
})
