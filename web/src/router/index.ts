import { createRouter, createWebHistory } from 'vue-router'

import BootstrapView from '@/views/BootstrapView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'bootstrap',
      component: BootstrapView,
    },
  ],
})

export default router
