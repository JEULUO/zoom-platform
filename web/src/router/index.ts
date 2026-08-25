import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import BootstrapView from '@/views/BootstrapView.vue'
import CampusView from '@/views/CampusView.vue'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'bootstrap',
      component: BootstrapView,
      meta: { requiresAuth: true },
    },
    {
      path: '/campuses',
      name: 'campuses',
      component: CampusView,
      meta: { requiresAuth: true, permission: 'campus.read' },
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  await authStore.initialize()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (typeof to.meta.permission === 'string' && !authStore.hasPermission(to.meta.permission)) {
    return { name: 'bootstrap' }
  }
  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'bootstrap' }
  }
  return true
})

export default router
