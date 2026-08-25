<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTooltip } from 'element-plus'
import 'element-plus/es/components/tooltip/style/css'
import {
  BookOpen,
  Building2,
  CircleDollarSign,
  Gauge,
  Menu,
  RefreshCw,
  Server,
  Users,
  X,
} from 'lucide-vue-next'

import { useSystemStore } from '@/stores/system'

const { t } = useI18n()
const systemStore = useSystemStore()
const sidebarOpen = ref(false)

const navigation = [
  { key: 'overview', icon: Gauge, active: true },
  { key: 'campus', icon: Building2 },
  { key: 'people', icon: Users },
  { key: 'courses', icon: BookOpen },
  { key: 'finance', icon: CircleDollarSign },
]

const statusLabel = computed(() => {
  if (systemStore.status === 'loading') return t('bootstrap.statusChecking')
  if (systemStore.status === 'ready') return t('bootstrap.statusReady')
  return t('bootstrap.statusUnavailable')
})

onMounted(() => systemStore.checkStatus())
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
        <button
          v-for="item in navigation"
          :key="item.key"
          class="navigation__item"
          :class="{ 'navigation__item--active': item.active }"
          type="button"
          :disabled="!item.active"
        >
          <component :is="item.icon" :size="19" aria-hidden="true" />
          <span>{{ t(`nav.${item.key}`) }}</span>
        </button>
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
          <span>全部校区</span>
        </div>
        <div class="profile-chip" aria-label="当前用户">
          <span>管</span>
          <strong>系统管理员</strong>
        </div>
      </header>

      <main class="content">
        <section class="page-heading">
          <div>
            <p class="eyebrow">{{ t('bootstrap.eyebrow') }}</p>
            <h1>{{ t('bootstrap.title') }}</h1>
            <p class="page-description">{{ t('bootstrap.description') }}</p>
          </div>
          <el-tooltip :content="t('bootstrap.refresh')" placement="bottom">
            <button
              class="refresh-button"
              type="button"
              :disabled="systemStore.status === 'loading'"
              :aria-label="t('bootstrap.refresh')"
              @click="systemStore.checkStatus"
            >
              <RefreshCw :size="18" :class="{ spinning: systemStore.status === 'loading' }" />
            </button>
          </el-tooltip>
        </section>

        <section class="status-band" aria-live="polite">
          <div class="status-band__summary">
            <span class="status-indicator" :class="`status-indicator--${systemStore.status}`" />
            <div>
              <small>{{ t('bootstrap.statusTitle') }}</small>
              <strong>{{ statusLabel }}</strong>
            </div>
          </div>
          <div class="status-band__meta">
            <span>{{ systemStore.service }}</span>
            <time v-if="systemStore.checkedAt" :datetime="systemStore.checkedAt">
              {{ new Date(systemStore.checkedAt).toLocaleTimeString('zh-CN', { hour12: false }) }}
            </time>
          </div>
        </section>

        <section class="service-section">
          <div class="section-heading">
            <div>
              <h2>{{ t('bootstrap.infrastructure') }}</h2>
              <p>开发环境依赖与服务连通性</p>
            </div>
          </div>

          <div class="service-grid">
            <article class="service-card service-card--primary">
              <div class="service-card__icon"><Server :size="22" /></div>
              <div class="service-card__body">
                <span>{{ t('bootstrap.backend') }}</span>
                <strong>Java 17 / Spring Boot 3.5</strong>
              </div>
              <span class="state-label" :class="{ 'state-label--ready': systemStore.isReady }">
                {{ systemStore.isReady ? t('bootstrap.ready') : statusLabel }}
              </span>
            </article>

            <article class="service-card">
              <div class="service-card__icon service-card__icon--blue"><Gauge :size="22" /></div>
              <div class="service-card__body">
                <span>{{ t('bootstrap.frontend') }}</span>
                <strong>Vue 3 / TypeScript</strong>
              </div>
              <span class="state-label state-label--ready">{{ t('bootstrap.ready') }}</span>
            </article>

            <article class="service-card">
              <div class="service-card__icon service-card__icon--amber"><Server :size="22" /></div>
              <div class="service-card__body">
                <span>{{ t('bootstrap.mysql') }}</span>
                <strong>localhost:3306</strong>
              </div>
              <span class="state-label">{{ t('bootstrap.pending') }}</span>
            </article>

            <article class="service-card">
              <div class="service-card__icon service-card__icon--red"><Server :size="22" /></div>
              <div class="service-card__body">
                <span>{{ t('bootstrap.redis') }}</span>
                <strong>localhost:6379</strong>
              </div>
              <span class="state-label">{{ t('bootstrap.pending') }}</span>
            </article>
          </div>
        </section>

        <section class="foundation-section">
          <div class="foundation-copy">
            <p class="eyebrow">01 / FOUNDATION</p>
            <h2>{{ t('bootstrap.stackTitle') }}</h2>
            <p>{{ t('bootstrap.stackDescription') }}</p>
          </div>
          <dl class="foundation-list">
            <div><dt>Runtime</dt><dd>Java 17 · Node 22</dd></div>
            <div><dt>Package</dt><dd>Maven Wrapper · pnpm 11</dd></div>
            <div><dt>Data</dt><dd>MySQL 8.4 · Redis 7.4</dd></div>
            <div><dt>Locale</dt><dd>zh-CN · UTC storage</dd></div>
          </dl>
        </section>

        <section class="next-step">
          <span>02</span>
          <div>
            <h2>{{ t('bootstrap.nextTitle') }}</h2>
            <p>{{ t('bootstrap.nextDescription') }}</p>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>
