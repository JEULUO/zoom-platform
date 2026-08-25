<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTooltip } from 'element-plus'
import 'element-plus/es/components/tooltip/style/css'
import {
  ChevronLeft,
  ChevronRight,
  Clock3,
  Eye,
  LoaderCircle,
  MapPin,
  Pencil,
  Plus,
  Power,
  PowerOff,
  RefreshCw,
  Search,
  ShieldCheck,
  UserRound,
  Users,
  X,
} from 'lucide-vue-next'

import AppShell from '@/components/AppShell.vue'
import { useAuthStore } from '@/stores/auth'
import {
  UserDirectoryRequestError,
  useUserDirectoryStore,
  type UserDetail,
  type UserFormValues,
  type UserSummary,
  type UserStatus,
} from '@/stores/user-directory'
import '@/styles/user-directory.css'

const { t } = useI18n()
const authStore = useAuthStore()
const directoryStore = useUserDirectoryStore()
const detailOpen = ref(false)
const detailBusy = ref(false)
const detailError = ref('')
const selectedUser = ref<UserDetail | null>(null)
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formBusy = ref(false)
const formError = ref('')
const editingId = ref<number | null>(null)
const editingVersion = ref(0)
const statusTarget = ref<UserSummary | null>(null)
const statusBusy = ref(false)
const notice = ref('')
const form = reactive<UserFormValues>(emptyForm())

const canManage = computed(() => authStore.hasPermission('user.manage'))

const scopeLabel = computed(() =>
  t(`users.scopes.${authStore.user?.dataScope ?? 'SELF'}`),
)
const listError = computed(() =>
  directoryStore.error ? errorMessage(directoryStore.error) : '',
)
const pageSummary = computed(() => {
  if (directoryStore.total === 0) return t('users.noRecords')
  const start = (directoryStore.page - 1) * directoryStore.pageSize + 1
  const end = Math.min(
    directoryStore.page * directoryStore.pageSize,
    directoryStore.total,
  )
  return t('users.pageSummary', { start, end, total: directoryStore.total })
})

function emptyForm(): UserFormValues {
  return {
    username: '',
    initialPassword: '',
    displayName: '',
    email: '',
    phone: '',
    preferredLanguage: 'zh-CN',
    timezone: 'Asia/Shanghai',
    status: 'ACTIVE',
    roleIds: [],
    campusIds: [],
    primaryCampusId: null,
  }
}

function resetForm(values: UserFormValues = emptyForm()) {
  Object.assign(form, values)
  formError.value = ''
}

function detailToForm(user: UserDetail): UserFormValues {
  return {
    username: user.username,
    initialPassword: '',
    displayName: user.displayName,
    email: user.email ?? '',
    phone: user.phone ?? '',
    preferredLanguage: user.preferredLanguage,
    timezone: user.timezone,
    status: user.status === 'PENDING' ? 'PENDING' : 'ACTIVE',
    roleIds: directoryStore.roles
      .filter((role) => user.roles.some((assigned) => assigned.code === role.code))
      .map((role) => role.id),
    campusIds: user.campuses.map((campus) => campus.id),
    primaryCampusId: user.campuses.find((campus) => campus.primaryCampus)?.id ?? null,
  }
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  editingVersion.value = 0
  resetForm()
  formOpen.value = true
}

async function openEdit(user: UserSummary) {
  formMode.value = 'edit'
  editingId.value = user.id
  formOpen.value = true
  formBusy.value = true
  formError.value = ''
  try {
    const detail = await directoryStore.fetchById(user.id)
    editingVersion.value = detail.version
    resetForm(detailToForm(detail))
  } catch (error) {
    formError.value = errorMessage(requestCode(error))
  } finally {
    formBusy.value = false
  }
}

function closeForm() {
  if (!formBusy.value) formOpen.value = false
}

function toggleCampus(campusId: number, checked: boolean) {
  form.campusIds = checked
    ? [...form.campusIds, campusId]
    : form.campusIds.filter((id) => id !== campusId)
  if (!form.campusIds.includes(form.primaryCampusId ?? -1)) {
    form.primaryCampusId = form.campusIds[0] ?? null
  }
}

async function submitForm() {
  if (formBusy.value) return
  formBusy.value = true
  formError.value = ''
  try {
    if (formMode.value === 'create') {
      await directoryStore.createUser({ ...form })
      showNotice(t('users.created'))
    } else if (editingId.value !== null) {
      await directoryStore.updateUser(editingId.value, { ...form }, editingVersion.value)
      showNotice(t('users.updated'))
    }
    formOpen.value = false
    await loadPage()
  } catch (error) {
    formError.value = errorMessage(requestCode(error))
  } finally {
    formBusy.value = false
  }
}

function requestStatusChange(user: UserSummary) {
  statusTarget.value = user
}

async function confirmStatusChange() {
  if (!statusTarget.value || statusBusy.value) return
  statusBusy.value = true
  const nextStatus = statusTarget.value.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED'
  try {
    await directoryStore.updateUserStatus(
      statusTarget.value.id,
      nextStatus,
      statusTarget.value.version,
    )
    showNotice(nextStatus === 'ACTIVE' ? t('users.activated') : t('users.disabled'))
    statusTarget.value = null
    await loadPage()
  } catch (error) {
    showNotice(errorMessage(requestCode(error)))
    statusTarget.value = null
  } finally {
    statusBusy.value = false
  }
}

async function loadPage(page = directoryStore.page) {
  try {
    await directoryStore.fetchPage(page)
  } catch {
    // The store exposes a stable error code for the page state.
  }
}

async function search() {
  directoryStore.page = 1
  await loadPage(1)
}

async function clearFilters() {
  directoryStore.keyword = ''
  directoryStore.statusFilter = ''
  directoryStore.campusFilter = ''
  await search()
}

async function openDetail(id: number) {
  selectedUser.value = null
  detailError.value = ''
  detailBusy.value = true
  detailOpen.value = true
  try {
    selectedUser.value = await directoryStore.fetchById(id)
  } catch (error) {
    detailError.value = errorMessage(requestCode(error))
  } finally {
    detailBusy.value = false
  }
}

function closeDetail() {
  if (!detailBusy.value) detailOpen.value = false
}

function requestCode(error: unknown) {
  return error instanceof UserDirectoryRequestError
    ? error.code
    : 'USER_DIRECTORY_REQUEST_FAILED'
}

function errorMessage(code: string) {
  const known = [
    'USER_NOT_FOUND',
    'USER_IDENTIFIER_EXISTS',
    'USER_VERSION_CONFLICT',
    'USER_ROLE_REQUIRED',
    'USER_CAMPUS_REQUIRED',
    'USER_PRIMARY_CAMPUS_INVALID',
    'USER_SELF_STATUS_FORBIDDEN',
    'USER_SELF_MANAGE_FORBIDDEN',
    'USER_STATUS_INVALID',
    'INVALID_TIMEZONE',
    'VALIDATION_FAILED',
  ].includes(code)
  return known ? t(`users.errors.${code}`) : t('users.errors.DEFAULT')
}

function showNotice(message: string) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 3000)
}

function statusLabel(status: UserStatus) {
  return t(`users.statuses.${status}`)
}

function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

onMounted(async () => {
  await Promise.allSettled([directoryStore.fetchOptions(), loadPage(1)])
})
</script>

<template>
  <AppShell :context-label="t('nav.people')">
    <section class="page-heading user-page-heading">
      <div>
        <p class="eyebrow">{{ t('users.eyebrow') }}</p>
        <h1>{{ t('users.title') }}</h1>
        <p class="page-description">{{ t('users.scopeLabel', { scope: scopeLabel }) }}</p>
      </div>
      <button v-if="canManage" class="primary-command" type="button" @click="openCreate">
        <Plus :size="18" />
        <span>{{ t('users.create') }}</span>
      </button>
    </section>

    <div v-if="notice" class="operation-notice" role="status">{{ notice }}</div>

    <section class="directory-toolbar" :aria-label="t('users.filtersLabel')">
      <form class="directory-search" @submit.prevent="search">
        <button class="directory-search__submit" type="submit" :aria-label="t('users.search')">
          <Search :size="18" aria-hidden="true" />
        </button>
        <input v-model="directoryStore.keyword" type="search" :placeholder="t('users.searchPlaceholder')" />
      </form>
      <select v-model="directoryStore.statusFilter" class="directory-filter" :aria-label="t('users.statusFilter')" @change="search">
        <option value="">{{ t('users.allStatuses') }}</option>
        <option v-for="status in directoryStore.statuses" :key="status" :value="status">{{ statusLabel(status) }}</option>
      </select>
      <select v-model.number="directoryStore.campusFilter" class="directory-filter" :aria-label="t('users.campusFilter')" @change="search">
        <option value="">{{ t('users.allCampuses') }}</option>
        <option v-for="campus in directoryStore.campuses" :key="campus.id" :value="campus.id">{{ campus.name }}</option>
      </select>
      <button class="secondary-command directory-clear" type="button" @click="clearFilters">
        <X :size="16" />
        <span>{{ t('users.clear') }}</span>
      </button>
      <el-tooltip :content="t('users.refresh')" placement="bottom">
        <button class="icon-button" type="button" :aria-label="t('users.refresh')" :disabled="directoryStore.loading" @click="loadPage()">
          <RefreshCw :size="18" :class="{ spinning: directoryStore.loading }" />
        </button>
      </el-tooltip>
    </section>

    <section class="directory-data" :aria-busy="directoryStore.loading">
      <div v-if="listError" class="directory-state directory-state--error" role="alert">
        <strong>{{ listError }}</strong>
        <button class="secondary-command" type="button" @click="loadPage()">{{ t('users.retry') }}</button>
      </div>

      <div v-else-if="!directoryStore.loading && directoryStore.items.length === 0" class="directory-state">
        <Users :size="28" />
        <strong>{{ t('users.empty') }}</strong>
      </div>

      <template v-else>
        <div class="directory-table-wrap">
          <table class="directory-table">
            <thead>
              <tr>
                <th>{{ t('users.columns.user') }}</th>
                <th>{{ t('users.columns.roles') }}</th>
                <th>{{ t('users.columns.campuses') }}</th>
                <th>{{ t('users.columns.status') }}</th>
                <th>{{ t('users.columns.lastLogin') }}</th>
                <th class="directory-table__actions">{{ t('users.columns.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in directoryStore.items" :key="user.id">
                <td>
                  <strong>{{ user.displayName }}</strong>
                  <code>@{{ user.username }}</code>
                </td>
                <td>
                  <div class="assignment-list">
                    <span v-for="role in user.roles" :key="role.code" class="role-label">{{ role.name }}</span>
                    <span v-if="user.roles.length === 0" class="muted-value">—</span>
                  </div>
                </td>
                <td>
                  <div class="assignment-list">
                    <span v-for="campus in user.campuses" :key="campus.id">{{ campus.name }}</span>
                    <span v-if="user.campuses.length === 0" class="muted-value">—</span>
                  </div>
                </td>
                <td><span class="user-status" :class="`user-status--${user.status.toLowerCase()}`">{{ statusLabel(user.status) }}</span></td>
                <td>{{ formatDate(user.lastLoginAt) }}</td>
                <td class="directory-table__actions">
                  <el-tooltip :content="t('users.view')" placement="top">
                    <button class="table-action" type="button" :aria-label="`${t('users.view')} ${user.displayName}`" @click="openDetail(user.id)"><Eye :size="17" /></button>
                  </el-tooltip>
                  <template v-if="canManage">
                    <el-tooltip :content="t('users.edit')" placement="top">
                      <button class="table-action" type="button" :disabled="user.id === authStore.user?.id" :aria-label="`${t('users.edit')} ${user.displayName}`" @click="openEdit(user)"><Pencil :size="16" /></button>
                    </el-tooltip>
                    <el-tooltip :content="user.status === 'DISABLED' ? t('users.activate') : t('users.disable')" placement="top">
                      <button class="table-action" type="button" :disabled="user.id === authStore.user?.id" :aria-label="`${user.status === 'DISABLED' ? t('users.activate') : t('users.disable')} ${user.displayName}`" @click="requestStatusChange(user)">
                        <Power v-if="user.status === 'DISABLED'" :size="16" />
                        <PowerOff v-else :size="16" />
                      </button>
                    </el-tooltip>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="directory-mobile-list">
          <article v-for="user in directoryStore.items" :key="user.id" class="directory-mobile-card">
            <header>
              <div class="user-avatar">{{ user.displayName.slice(0, 1).toUpperCase() }}</div>
              <div><strong>{{ user.displayName }}</strong><code>@{{ user.username }}</code></div>
              <span class="user-status" :class="`user-status--${user.status.toLowerCase()}`">{{ statusLabel(user.status) }}</span>
            </header>
            <dl>
              <div><dt>{{ t('users.columns.roles') }}</dt><dd>{{ user.roles.map((role) => role.name).join('、') || '—' }}</dd></div>
              <div><dt>{{ t('users.columns.campuses') }}</dt><dd>{{ user.campuses.map((campus) => campus.name).join('、') || '—' }}</dd></div>
            </dl>
            <div class="directory-mobile-actions">
              <button class="secondary-command" type="button" @click="openDetail(user.id)"><Eye :size="16" />{{ t('users.view') }}</button>
              <button v-if="canManage && user.id !== authStore.user?.id" class="secondary-command" type="button" @click="openEdit(user)"><Pencil :size="16" />{{ t('users.edit') }}</button>
              <button v-if="canManage && user.id !== authStore.user?.id" class="secondary-command" type="button" @click="requestStatusChange(user)">
                <Power v-if="user.status === 'DISABLED'" :size="16" />
                <PowerOff v-else :size="16" />
                {{ user.status === 'DISABLED' ? t('users.activate') : t('users.disable') }}
              </button>
            </div>
          </article>
        </div>

        <footer class="directory-pagination">
          <span>{{ pageSummary }}</span>
          <div>
            <button class="icon-button" type="button" :aria-label="t('users.previousPage')" :disabled="directoryStore.page <= 1 || directoryStore.loading" @click="loadPage(directoryStore.page - 1)"><ChevronLeft :size="18" /></button>
            <strong>{{ directoryStore.page }} / {{ directoryStore.totalPages }}</strong>
            <button class="icon-button" type="button" :aria-label="t('users.nextPage')" :disabled="directoryStore.page >= directoryStore.totalPages || directoryStore.loading" @click="loadPage(directoryStore.page + 1)"><ChevronRight :size="18" /></button>
          </div>
        </footer>
      </template>

      <div v-if="directoryStore.loading" class="directory-loading" :aria-label="t('users.loading')"><LoaderCircle class="spinning" :size="24" /></div>
    </section>

    <div v-if="detailOpen" class="modal-backdrop" @mousedown.self="closeDetail">
      <section class="user-detail-modal" role="dialog" aria-modal="true" aria-labelledby="user-detail-title">
        <header class="user-detail-modal__header">
          <div><p class="eyebrow">{{ selectedUser ? `@${selectedUser.username}` : t('users.loading') }}</p><h2 id="user-detail-title">{{ selectedUser?.displayName || t('users.detailTitle') }}</h2></div>
          <button class="icon-button" type="button" :aria-label="t('users.close')" :disabled="detailBusy" @click="closeDetail"><X :size="19" /></button>
        </header>

        <div v-if="detailBusy" class="user-detail-state"><LoaderCircle class="spinning" :size="24" /></div>
        <div v-else-if="detailError" class="user-detail-state user-detail-state--error" role="alert">{{ detailError }}</div>
        <div v-else-if="selectedUser" class="user-detail-content">
          <section class="user-detail-identity">
            <div class="user-detail-avatar"><UserRound :size="26" /></div>
            <div><strong>{{ selectedUser.displayName }}</strong><span>{{ selectedUser.email || selectedUser.phone || '—' }}</span></div>
            <span class="user-status" :class="`user-status--${selectedUser.status.toLowerCase()}`">{{ statusLabel(selectedUser.status) }}</span>
          </section>

          <section class="user-detail-section">
            <h3><UserRound :size="17" />{{ t('users.profile') }}</h3>
            <dl class="user-detail-grid">
              <div><dt>{{ t('users.fields.username') }}</dt><dd>{{ selectedUser.username }}</dd></div>
              <div><dt>{{ t('users.fields.email') }}</dt><dd>{{ selectedUser.email || '—' }}</dd></div>
              <div><dt>{{ t('users.fields.phone') }}</dt><dd>{{ selectedUser.phone || '—' }}</dd></div>
              <div><dt>{{ t('users.fields.language') }}</dt><dd>{{ selectedUser.preferredLanguage }}</dd></div>
              <div><dt>{{ t('users.fields.timezone') }}</dt><dd>{{ selectedUser.timezone }}</dd></div>
              <div><dt>{{ t('users.fields.created') }}</dt><dd>{{ formatDate(selectedUser.createdAt) }}</dd></div>
            </dl>
          </section>

          <section class="user-detail-section">
            <h3><ShieldCheck :size="17" />{{ t('users.rolesTitle') }}</h3>
            <div class="detail-assignment-list">
              <div v-for="role in selectedUser.roles" :key="role.code"><strong>{{ role.name }}</strong><code>{{ role.code }}</code><span>{{ t(`users.scopes.${role.dataScope}`) }}</span></div>
              <p v-if="selectedUser.roles.length === 0" class="muted-value">{{ t('users.noRoles') }}</p>
            </div>
          </section>

          <section class="user-detail-section">
            <h3><MapPin :size="17" />{{ t('users.campusesTitle') }}</h3>
            <div class="detail-assignment-list">
              <div v-for="campus in selectedUser.campuses" :key="campus.id"><strong>{{ campus.name }}</strong><code>{{ campus.code }}</code><span>{{ campus.primaryCampus ? t('users.primaryCampus') : t('users.assignedCampus') }}</span></div>
              <p v-if="selectedUser.campuses.length === 0" class="muted-value">{{ t('users.noCampuses') }}</p>
            </div>
          </section>

          <section class="user-detail-section">
            <h3><Clock3 :size="17" />{{ t('users.securityTitle') }}</h3>
            <dl class="user-detail-grid">
              <div><dt>{{ t('users.fields.lastLogin') }}</dt><dd>{{ formatDate(selectedUser.lastLoginAt) }}</dd></div>
              <div><dt>{{ t('users.fields.passwordChanged') }}</dt><dd>{{ formatDate(selectedUser.passwordChangedAt) }}</dd></div>
              <div><dt>{{ t('users.fields.failedAttempts') }}</dt><dd>{{ selectedUser.failedLoginAttempts }}</dd></div>
              <div><dt>{{ t('users.fields.lockedUntil') }}</dt><dd>{{ formatDate(selectedUser.lockedUntil) }}</dd></div>
            </dl>
          </section>
        </div>
      </section>
    </div>

    <div v-if="formOpen" class="modal-backdrop" @mousedown.self="closeForm">
      <section class="user-form-modal" role="dialog" aria-modal="true" aria-labelledby="user-form-title">
        <header class="user-detail-modal__header">
          <div>
            <p class="eyebrow">{{ formMode === 'create' ? t('users.createEyebrow') : `@${form.username}` }}</p>
            <h2 id="user-form-title">{{ formMode === 'create' ? t('users.createTitle') : t('users.editTitle') }}</h2>
          </div>
          <button class="icon-button" type="button" :aria-label="t('users.closeForm')" :disabled="formBusy" @click="closeForm"><X :size="19" /></button>
        </header>

        <form class="user-form" @submit.prevent="submitForm">
          <div v-if="formError" class="user-form-error" role="alert">{{ formError }}</div>
          <div class="user-form-grid">
            <label>
              <span>{{ t('users.fields.username') }}</span>
              <input v-model.trim="form.username" required maxlength="64" pattern="[A-Za-z0-9._-]+" :disabled="formMode === 'edit'" />
            </label>
            <label v-if="formMode === 'create'">
              <span>{{ t('users.fields.initialPassword') }}</span>
              <input v-model="form.initialPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password" />
            </label>
            <label>
              <span>{{ t('users.fields.displayName') }}</span>
              <input v-model.trim="form.displayName" required maxlength="100" />
            </label>
            <label>
              <span>{{ t('users.fields.email') }}</span>
              <input v-model.trim="form.email" type="email" maxlength="160" />
            </label>
            <label>
              <span>{{ t('users.fields.phone') }}</span>
              <input v-model.trim="form.phone" maxlength="32" />
            </label>
            <label>
              <span>{{ t('users.fields.language') }}</span>
              <select v-model="form.preferredLanguage"><option value="zh-CN">简体中文</option><option value="en-GB">English (UK)</option></select>
            </label>
            <label>
              <span>{{ t('users.fields.timezone') }}</span>
              <input v-model.trim="form.timezone" required maxlength="64" />
            </label>
            <label v-if="formMode === 'create'">
              <span>{{ t('users.fields.status') }}</span>
              <select v-model="form.status"><option value="ACTIVE">{{ statusLabel('ACTIVE') }}</option><option value="PENDING">{{ statusLabel('PENDING') }}</option></select>
            </label>
          </div>

          <fieldset class="user-assignment-fieldset">
            <legend>{{ t('users.rolesTitle') }}</legend>
            <label v-for="role in directoryStore.roles" :key="role.id" class="assignment-option">
              <input v-model="form.roleIds" type="checkbox" :value="role.id" />
              <span><strong>{{ role.name }}</strong><small>{{ t(`users.scopes.${role.dataScope}`) }}</small></span>
            </label>
          </fieldset>

          <fieldset class="user-assignment-fieldset">
            <legend>{{ t('users.campusesTitle') }}</legend>
            <div v-for="campus in directoryStore.campuses" :key="campus.id" class="assignment-option assignment-option--campus">
              <label class="assignment-option__main">
                <input type="checkbox" :checked="form.campusIds.includes(campus.id)" @change="toggleCampus(campus.id, ($event.target as HTMLInputElement).checked)" />
                <span><strong>{{ campus.name }}</strong><small>{{ campus.code }}</small></span>
              </label>
              <label v-if="form.campusIds.includes(campus.id)" class="primary-campus-choice">
                <input v-model="form.primaryCampusId" type="radio" :value="campus.id" />
                <span>{{ t('users.primaryCampus') }}</span>
              </label>
            </div>
            <p v-if="directoryStore.campuses.length === 0" class="muted-value">{{ t('users.noCampusesAvailable') }}</p>
          </fieldset>

          <footer class="user-form-footer">
            <button class="secondary-command" type="button" :disabled="formBusy" @click="closeForm">{{ t('users.cancel') }}</button>
            <button class="primary-command" type="submit" :disabled="formBusy || form.roleIds.length === 0">
              <LoaderCircle v-if="formBusy" class="spinning" :size="17" />
              <span>{{ formBusy ? t('users.saving') : t('users.save') }}</span>
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="statusTarget" class="modal-backdrop" @mousedown.self="statusTarget = null">
      <section class="confirm-dialog" role="alertdialog" aria-modal="true">
        <div class="confirm-dialog__icon"><PowerOff :size="23" /></div>
        <h2>{{ statusTarget.status === 'DISABLED' ? t('users.confirmActivate') : t('users.confirmDisable') }}</h2>
        <p>{{ statusTarget.displayName }}</p>
        <footer>
          <button class="secondary-command" type="button" :disabled="statusBusy" @click="statusTarget = null">{{ t('users.cancel') }}</button>
          <button class="primary-command" type="button" :disabled="statusBusy" @click="confirmStatusChange">{{ t('users.confirm') }}</button>
        </footer>
      </section>
    </div>
  </AppShell>
</template>
