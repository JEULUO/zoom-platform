<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElTooltip } from 'element-plus'
import 'element-plus/es/components/tooltip/style/css'
import {
  BookOpen,
  Building2,
  CircleDollarSign,
  Gauge,
  LogOut,
  Menu,
  Users,
  X,
} from 'lucide-vue-next'

import { useAuthStore } from '@/stores/auth'

defineProps<{
  contextLabel: string
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const sidebarOpen = ref(false)

const navigation = [
  { key: 'overview', icon: Gauge, routeName: 'bootstrap' },
  { key: 'campus', icon: Building2, routeName: 'campuses', permission: 'campus.read' },
  { key: 'people', icon: Users },
  { key: 'courses', icon: BookOpen },
  { key: 'finance', icon: CircleDollarSign },
]

function isEnabled(item: (typeof navigation)[number]) {
  return Boolean(item.routeName) && (!item.permission || authStore.hasPermission(item.permission))
}

async function logout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--navigation-open': sidebarOpen }">
    <aside class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <div class="brand">
        <span class="brand__mark">ZE</span>
        <span>
          <strong>{{ t('brand.name') }}</strong>
          <small>{{ t('brand.product') }}</small>
        </span>
        <button class="sidebar-close" type="button" aria-label="关闭导航" @click="sidebarOpen = false">
          <X :size="20" />
        </button>
      </div>

      <nav class="navigation" aria-label="主导航">
        <template v-for="item in navigation" :key="item.key">
          <RouterLink
            v-if="isEnabled(item)"
            :to="{ name: item.routeName }"
            class="navigation__item"
            :class="{ 'navigation__item--active': route.name === item.routeName }"
            @click="sidebarOpen = false"
          >
            <component :is="item.icon" :size="19" aria-hidden="true" />
            <span>{{ t(`nav.${item.key}`) }}</span>
          </RouterLink>
          <button v-else class="navigation__item" type="button" disabled>
            <component :is="item.icon" :size="19" aria-hidden="true" />
            <span>{{ t(`nav.${item.key}`) }}</span>
          </button>
        </template>
      </nav>

      <div class="sidebar__footer">
        <span class="environment-dot" />
        <span>Development</span>
        <code>v0.1.0</code>
      </div>
    </aside>

    <button
      v-if="sidebarOpen"
      class="sidebar-backdrop"
      type="button"
      aria-label="关闭导航"
      @click="sidebarOpen = false"
    />

    <div class="workspace">
      <header class="topbar">
        <button
          class="icon-button mobile-menu"
          type="button"
          :aria-label="sidebarOpen ? '关闭导航' : '打开导航'"
          @click="sidebarOpen = !sidebarOpen"
        >
          <X v-if="sidebarOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
        <div class="topbar__context">
          <Building2 :size="17" aria-hidden="true" />
          <span>{{ contextLabel }}</span>
        </div>
        <div class="profile-actions">
          <div class="profile-chip" aria-label="当前用户">
            <span>{{ authStore.user?.displayName.slice(0, 1) }}</span>
            <strong>{{ authStore.user?.displayName }}</strong>
          </div>
          <el-tooltip :content="t('auth.logout')" placement="bottom">
            <button class="icon-button profile-logout" type="button" :aria-label="t('auth.logout')" @click="logout">
              <LogOut :size="18" />
            </button>
          </el-tooltip>
        </div>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>
