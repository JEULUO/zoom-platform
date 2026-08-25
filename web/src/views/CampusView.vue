<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElTooltip } from 'element-plus'
import 'element-plus/es/components/tooltip/style/css'
import {
  Building2,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  Pencil,
  Plus,
  Power,
  PowerOff,
  RefreshCw,
  Search,
  X,
} from 'lucide-vue-next'

import AppShell from '@/components/AppShell.vue'
import { useAuthStore } from '@/stores/auth'
import {
  CampusRequestError,
  useCampusStore,
  type CampusDetail,
  type CampusFormValues,
  type CampusStatus,
  type CampusSummary,
} from '@/stores/campus'

const { t } = useI18n()
const authStore = useAuthStore()
const campusStore = useCampusStore()

const canManage = computed(() => authStore.hasPermission('campus.manage'))
const canCreate = computed(() => canManage.value && authStore.user?.dataScope === 'ALL')
const scopeLabel = computed(() =>
  t(`campus.scopes.${authStore.user?.dataScope ?? 'SELF'}`),
)
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formBusy = ref(false)
const formError = ref<string | null>(null)
const editingId = ref<number | null>(null)
const editingVersion = ref(0)
const statusTarget = ref<CampusSummary | null>(null)
const statusBusy = ref(false)
const notice = ref('')

const form = reactive<CampusFormValues>(emptyForm())

const pageSummary = computed(() => {
  if (campusStore.total === 0) return t('campus.noRecords')
  const start = (campusStore.page - 1) * campusStore.pageSize + 1
  const end = Math.min(campusStore.page * campusStore.pageSize, campusStore.total)
  return t('campus.pageSummary', { start, end, total: campusStore.total })
})

const listError = computed(() =>
  campusStore.error ? errorMessage(campusStore.error) : '',
)

function emptyForm(): CampusFormValues {
  return {
    code: '',
    name: '',
    legalName: '',
    timezone: 'Europe/London',
    countryCode: 'GB',
    addressLine1: '',
    addressLine2: '',
    city: '',
    postalCode: '',
    contactEmail: '',
    contactPhone: '',
    sortOrder: 0,
  }
}

function resetForm(values: CampusFormValues = emptyForm()) {
  Object.assign(form, values)
  formError.value = null
}

function detailToForm(campus: CampusDetail): CampusFormValues {
  return {
    code: campus.code,
    name: campus.name,
    legalName: campus.legalName ?? '',
    timezone: campus.timezone,
    countryCode: campus.countryCode,
    addressLine1: campus.addressLine1 ?? '',
    addressLine2: campus.addressLine2 ?? '',
    city: campus.city ?? '',
    postalCode: campus.postalCode ?? '',
    contactEmail: campus.contactEmail ?? '',
    contactPhone: campus.contactPhone ?? '',
    sortOrder: campus.sortOrder,
  }
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  editingVersion.value = 0
  resetForm()
  formOpen.value = true
}

async function openEdit(campus: CampusSummary) {
  formMode.value = 'edit'
  editingId.value = campus.id
  formOpen.value = true
  formBusy.value = true
  formError.value = null
  try {
    const detail = await campusStore.fetchById(campus.id)
    editingVersion.value = detail.version
    resetForm(detailToForm(detail))
  } catch (error) {
    formError.value = requestCode(error)
  } finally {
    formBusy.value = false
  }
}

function closeForm() {
  if (!formBusy.value) formOpen.value = false
}

async function submitForm() {
  if (formBusy.value) return
  formBusy.value = true
  formError.value = null
  try {
    if (formMode.value === 'create') {
      await campusStore.createCampus({ ...form })
      showNotice(t('campus.created'))
    } else if (editingId.value !== null) {
      await campusStore.updateCampus(editingId.value, { ...form }, editingVersion.value)
      showNotice(t('campus.updated'))
    }
    formOpen.value = false
    await campusStore.fetchPage()
  } catch (error) {
    formError.value = requestCode(error)
  } finally {
    formBusy.value = false
  }
}

function requestStatusChange(campus: CampusSummary) {
  statusTarget.value = campus
}

async function confirmStatusChange() {
  if (!statusTarget.value || statusBusy.value) return
  statusBusy.value = true
  const nextStatus: CampusStatus = statusTarget.value.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await campusStore.updateCampusStatus(
      statusTarget.value.id,
      nextStatus,
      statusTarget.value.version,
    )
    showNotice(nextStatus === 'ACTIVE' ? t('campus.activated') : t('campus.deactivated'))
    statusTarget.value = null
    await campusStore.fetchPage()
  } catch (error) {
    showNotice(errorMessage(requestCode(error)))
    statusTarget.value = null
  } finally {
    statusBusy.value = false
  }
}

async function search() {
  campusStore.page = 1
  await loadPage(1)
}

async function clearFilters() {
  campusStore.keyword = ''
  campusStore.statusFilter = ''
  await search()
}

async function loadPage(page = campusStore.page) {
  try {
    await campusStore.fetchPage(page)
  } catch {
    // The store exposes a stable error code for the page state.
  }
}

function requestCode(error: unknown) {
  return error instanceof CampusRequestError ? error.code : 'CAMPUS_REQUEST_FAILED'
}

function errorMessage(code: string) {
  const known = [
    'CAMPUS_CODE_EXISTS',
    'CAMPUS_VERSION_CONFLICT',
    'INVALID_TIMEZONE',
    'VALIDATION_FAILED',
  ].includes(code)
  return known ? t(`campus.errors.${code}`) : t('campus.errors.DEFAULT')
}

function showNotice(message: string) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 3000)
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

onMounted(() => loadPage(1))
</script>

<template>
  <AppShell :context-label="t('nav.campus')">
    <section class="page-heading campus-page-heading">
      <div>
        <p class="eyebrow">{{ t('campus.eyebrow') }}</p>
        <h1>{{ t('campus.title') }}</h1>
        <p class="page-description">{{ t('campus.scopeLabel', { scope: scopeLabel }) }}</p>
      </div>
      <button v-if="canCreate" class="primary-command" type="button" @click="openCreate">
        <Plus :size="18" />
        <span>{{ t('campus.create') }}</span>
      </button>
    </section>

    <div v-if="notice" class="operation-notice" role="status">{{ notice }}</div>

    <section class="campus-toolbar" aria-label="校区筛选">
      <form class="campus-search" @submit.prevent="search">
        <button class="campus-search__submit" type="submit" :aria-label="t('campus.search')">
          <Search :size="18" aria-hidden="true" />
        </button>
        <input v-model="campusStore.keyword" type="search" :placeholder="t('campus.searchPlaceholder')" />
      </form>
      <select v-model="campusStore.statusFilter" class="campus-filter" :aria-label="t('campus.statusFilter')" @change="search">
        <option value="">{{ t('campus.allStatuses') }}</option>
        <option value="ACTIVE">{{ t('campus.active') }}</option>
        <option value="INACTIVE">{{ t('campus.inactive') }}</option>
      </select>
      <button class="secondary-command" type="button" @click="clearFilters">
        <X :size="16" />
        <span>{{ t('campus.clear') }}</span>
      </button>
      <el-tooltip :content="t('campus.refresh')" placement="bottom">
        <button class="icon-button" type="button" :aria-label="t('campus.refresh')" :disabled="campusStore.loading" @click="loadPage()">
          <RefreshCw :size="18" :class="{ spinning: campusStore.loading }" />
        </button>
      </el-tooltip>
    </section>

    <section class="campus-data" :aria-busy="campusStore.loading">
      <div v-if="listError" class="campus-state campus-state--error" role="alert">
        <strong>{{ listError }}</strong>
        <button class="secondary-command" type="button" @click="loadPage()">{{ t('campus.retry') }}</button>
      </div>

      <div v-else-if="!campusStore.loading && campusStore.items.length === 0" class="campus-state">
        <Building2 :size="28" />
        <strong>{{ t('campus.empty') }}</strong>
      </div>

      <template v-else>
        <div class="campus-table-wrap">
          <table class="campus-table">
            <thead>
              <tr>
                <th>{{ t('campus.columns.campus') }}</th>
                <th>{{ t('campus.columns.location') }}</th>
                <th>{{ t('campus.columns.timezone') }}</th>
                <th>{{ t('campus.columns.status') }}</th>
                <th>{{ t('campus.columns.updated') }}</th>
                <th class="campus-table__actions">{{ t('campus.columns.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="campus in campusStore.items" :key="campus.id">
                <td>
                  <strong>{{ campus.name }}</strong>
                  <code>{{ campus.code }}</code>
                </td>
                <td>{{ campus.city || '—' }} · {{ campus.countryCode }}</td>
                <td>{{ campus.timezone }}</td>
                <td>
                  <span class="campus-status" :class="`campus-status--${campus.status.toLowerCase()}`">
                    {{ campus.status === 'ACTIVE' ? t('campus.active') : t('campus.inactive') }}
                  </span>
                </td>
                <td>{{ formatDate(campus.updatedAt) }}</td>
                <td class="campus-table__actions">
                  <el-tooltip v-if="canManage" :content="t('campus.edit')" placement="top">
                    <button class="table-action" type="button" :aria-label="`${t('campus.edit')} ${campus.name}`" @click="openEdit(campus)">
                      <Pencil :size="17" />
                    </button>
                  </el-tooltip>
                  <el-tooltip v-if="canManage" :content="campus.status === 'ACTIVE' ? t('campus.deactivate') : t('campus.activate')" placement="top">
                    <button class="table-action" type="button" :aria-label="`${campus.status === 'ACTIVE' ? t('campus.deactivate') : t('campus.activate')} ${campus.name}`" @click="requestStatusChange(campus)">
                      <PowerOff v-if="campus.status === 'ACTIVE'" :size="17" />
                      <Power v-else :size="17" />
                    </button>
                  </el-tooltip>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="campus-mobile-list">
          <article v-for="campus in campusStore.items" :key="campus.id" class="campus-mobile-card">
            <div class="campus-mobile-card__heading">
              <div><strong>{{ campus.name }}</strong><code>{{ campus.code }}</code></div>
              <span class="campus-status" :class="`campus-status--${campus.status.toLowerCase()}`">
                {{ campus.status === 'ACTIVE' ? t('campus.active') : t('campus.inactive') }}
              </span>
            </div>
            <dl>
              <div><dt>{{ t('campus.columns.location') }}</dt><dd>{{ campus.city || '—' }} · {{ campus.countryCode }}</dd></div>
              <div><dt>{{ t('campus.columns.timezone') }}</dt><dd>{{ campus.timezone }}</dd></div>
            </dl>
            <div v-if="canManage" class="campus-mobile-card__actions">
              <button class="secondary-command" type="button" @click="openEdit(campus)"><Pencil :size="16" />{{ t('campus.edit') }}</button>
              <button class="secondary-command" type="button" @click="requestStatusChange(campus)">
                <PowerOff v-if="campus.status === 'ACTIVE'" :size="16" /><Power v-else :size="16" />
                {{ campus.status === 'ACTIVE' ? t('campus.deactivate') : t('campus.activate') }}
              </button>
            </div>
          </article>
        </div>

        <footer class="campus-pagination">
          <span>{{ pageSummary }}</span>
          <div>
            <button class="icon-button" type="button" :aria-label="t('campus.previousPage')" :disabled="campusStore.page <= 1 || campusStore.loading" @click="loadPage(campusStore.page - 1)"><ChevronLeft :size="18" /></button>
            <strong>{{ campusStore.page }} / {{ campusStore.totalPages }}</strong>
            <button class="icon-button" type="button" :aria-label="t('campus.nextPage')" :disabled="campusStore.page >= campusStore.totalPages || campusStore.loading" @click="loadPage(campusStore.page + 1)"><ChevronRight :size="18" /></button>
          </div>
        </footer>
      </template>

      <div v-if="campusStore.loading" class="campus-loading" aria-label="正在加载">
        <LoaderCircle class="spinning" :size="24" />
      </div>
    </section>

    <div v-if="formOpen" class="modal-backdrop" @mousedown.self="closeForm">
      <section class="campus-modal" role="dialog" aria-modal="true" :aria-labelledby="'campus-form-title'">
        <header class="campus-modal__header">
          <div><p class="eyebrow">{{ formMode === 'create' ? t('campus.createEyebrow') : form.code }}</p><h2 id="campus-form-title">{{ formMode === 'create' ? t('campus.createTitle') : t('campus.editTitle') }}</h2></div>
          <button class="icon-button" type="button" :aria-label="t('campus.close')" :disabled="formBusy" @click="closeForm"><X :size="19" /></button>
        </header>
        <form class="campus-form" @submit.prevent="submitForm">
          <div v-if="formError" class="auth-error" role="alert">{{ errorMessage(formError) }}</div>
          <div class="campus-form__grid">
            <label><span>{{ t('campus.fields.code') }}</span><input v-model.trim="form.code" :disabled="formMode === 'edit'" required maxlength="32" pattern="[A-Za-z0-9_-]+" /></label>
            <label><span>{{ t('campus.fields.name') }}</span><input v-model.trim="form.name" required maxlength="100" /></label>
            <label class="campus-form__wide"><span>{{ t('campus.fields.legalName') }}</span><input v-model.trim="form.legalName" maxlength="160" /></label>
            <label><span>{{ t('campus.fields.timezone') }}</span><select v-model="form.timezone" required><option value="Europe/London">Europe/London</option><option value="Asia/Shanghai">Asia/Shanghai</option><option value="Europe/Paris">Europe/Paris</option><option value="America/New_York">America/New_York</option></select></label>
            <label><span>{{ t('campus.fields.countryCode') }}</span><input v-model.trim="form.countryCode" required minlength="2" maxlength="2" /></label>
            <label class="campus-form__wide"><span>{{ t('campus.fields.address1') }}</span><input v-model.trim="form.addressLine1" maxlength="160" /></label>
            <label class="campus-form__wide"><span>{{ t('campus.fields.address2') }}</span><input v-model.trim="form.addressLine2" maxlength="160" /></label>
            <label><span>{{ t('campus.fields.city') }}</span><input v-model.trim="form.city" maxlength="80" /></label>
            <label><span>{{ t('campus.fields.postalCode') }}</span><input v-model.trim="form.postalCode" maxlength="20" /></label>
            <label><span>{{ t('campus.fields.email') }}</span><input v-model.trim="form.contactEmail" type="email" maxlength="160" /></label>
            <label><span>{{ t('campus.fields.phone') }}</span><input v-model.trim="form.contactPhone" type="tel" maxlength="32" /></label>
            <label><span>{{ t('campus.fields.sortOrder') }}</span><input v-model.number="form.sortOrder" type="number" min="0" max="100000" /></label>
          </div>
          <footer class="campus-modal__footer">
            <button class="secondary-command" type="button" :disabled="formBusy" @click="closeForm">{{ t('campus.cancel') }}</button>
            <button class="primary-command" type="submit" :disabled="formBusy"><LoaderCircle v-if="formBusy" class="spinning" :size="17" />{{ formBusy ? t('campus.saving') : t('campus.save') }}</button>
          </footer>
        </form>
      </section>
    </div>

    <div v-if="statusTarget" class="modal-backdrop" @mousedown.self="statusTarget = null">
      <section class="confirm-dialog" role="alertdialog" aria-modal="true">
        <div class="confirm-dialog__icon"><PowerOff v-if="statusTarget.status === 'ACTIVE'" :size="22" /><Power v-else :size="22" /></div>
        <h2>{{ statusTarget.status === 'ACTIVE' ? t('campus.confirmDeactivate') : t('campus.confirmActivate') }}</h2>
        <p>{{ statusTarget.name }} · {{ statusTarget.code }}</p>
        <footer><button class="secondary-command" type="button" :disabled="statusBusy" @click="statusTarget = null">{{ t('campus.cancel') }}</button><button class="primary-command" type="button" :disabled="statusBusy" @click="confirmStatusChange">{{ t('campus.confirm') }}</button></footer>
      </section>
    </div>
  </AppShell>
</template>
