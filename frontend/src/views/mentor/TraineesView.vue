<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Menu from 'primevue/menu'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import type { MenuItem } from 'primevue/menuitem'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type { CurriculumResponse, TraineeResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'
import { groupCurriculaByFamily } from '../../utils/curriculumGroups'

const toast = useToast()

const query = ref('')
const activeFilter = ref<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL')
const rows = ref<TraineeResponse[]>([])
const totalRecords = ref(0)
const curricula = ref<CurriculumResponse[]>([])
const loading = ref(false)
const error = ref('')

const createDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const createEmail = ref('')
const createName = ref('')
const assignTrainee = ref<TraineeResponse | null>(null)
const assignFamilyGroupId = ref<number | null>(null)
const assignVersionId = ref<number | null>(null)
const assignMode = ref<'assign' | 'replace'>('assign')

const first = ref(0)
const pageRows = ref(8)
const actionMenu = ref()
const actionMenuItems = ref<MenuItem[]>([])

const summaryCount = computed(() => totalRecords.value)

const assignCurriculumResolved = computed(() => {
  if (assignVersionId.value == null) return null
  return curricula.value.find((c) => c.id === assignVersionId.value) ?? null
})

const assignFamilyOptions = computed(() =>
  groupCurriculaByFamily(curricula.value).map((g) => ({
    label:
      g.versionCount > 1
        ? `${g.representative.name} (${g.versionCount} published versions)`
        : g.representative.name,
    value: g.representative.curriculumGroupId,
  })),
)

function versionsInGroup(groupId: number): CurriculumResponse[] {
  return [...curricula.value]
    .filter((c) => (c.curriculumGroupId ?? c.id) === groupId)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
}

const assignVersionOptions = computed(() => {
  if (assignFamilyGroupId.value == null) return []
  return versionsInGroup(assignFamilyGroupId.value).map((c) => ({
    label: `${c.versionLabel} · ${c.status} · ${formatDate(c.updatedAt)}`,
    value: c.id,
  }))
})

watch(assignFamilyGroupId, (gid) => {
  if (gid == null) {
    assignVersionId.value = null
    return
  }
  const v = versionsInGroup(gid)
  assignVersionId.value = v[0]?.id ?? null
})

const activeFilterOptions = [
  { label: 'All statuses', value: 'ALL' as const },
  { label: 'Active', value: 'ACTIVE' as const },
  { label: 'Inactive', value: 'INACTIVE' as const },
]

function formatDate(value?: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((p) => p[0]?.toUpperCase() ?? '').join('') || 'TR'
}

function goToFirstPage(): void {
  first.value = 0
}

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const active = activeFilter.value === 'ALL' ? undefined : activeFilter.value === 'ACTIVE'
    const page = Math.floor(first.value / pageRows.value)
    const res = await mentorApi.listTrainees({
      q: query.value,
      active,
      page,
      size: pageRows.value,
      sortBy: 'createdAt',
      sortDir: 'desc',
    })
    rows.value = res.items
    totalRecords.value = res.totalElements

    // Keep paginator in valid range when current page no longer has rows.
    if (res.items.length === 0 && res.totalElements > 0 && first.value >= res.totalElements) {
      const lastFirst = Math.max(0, (Math.ceil(res.totalElements / pageRows.value) - 1) * pageRows.value)
      if (lastFirst !== first.value) {
        first.value = lastFirst
        await load()
      }
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load trainees'
  } finally {
    loading.value = false
  }
}

async function loadCurricula(): Promise<void> {
  try {
    curricula.value = await mentorApi.listAllCurricula({
      status: 'PUBLISHED',
      sortBy: 'updatedAt',
      sortDir: 'desc',
      size: 100,
    })
  } catch {
    /* optional for assign flow */
  }
}

onMounted(() => {
  void load()
  void loadCurricula()
})

watch(query, () => {
  if (!query.value.trim()) {
    goToFirstPage()
    void load()
  }
})

watch(activeFilter, () => {
  goToFirstPage()
  void load()
})

async function search(): Promise<void> {
  goToFirstPage()
  await load()
}

async function resetFilters(): Promise<void> {
  query.value = ''
  activeFilter.value = 'ALL'
  goToFirstPage()
  await load()
}

async function createTrainee(): Promise<void> {
  error.value = ''
  try {
    const res = await mentorApi.createTrainee({ email: createEmail.value, fullName: createName.value })
    createDialogVisible.value = false
    createEmail.value = ''
    createName.value = ''
    toast.add({
      severity: 'success',
      summary: 'Trainee created',
      detail: `${res.email} | Temporary password: ${res.temporaryPassword}`,
      life: 7000,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Create failed'
  }
}

async function toggleActive(row: TraineeResponse): Promise<void> {
  error.value = ''
  try {
    await mentorApi.setTraineeActive(row.id, !row.active)
    toast.add({
      severity: 'success',
      summary: 'Status updated',
      detail: `${row.fullName} is now ${row.active ? 'inactive' : 'active'}.`,
      life: 3500,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Update failed'
  }
}

async function resetPassword(row: TraineeResponse): Promise<void> {
  error.value = ''
  try {
    const res = await mentorApi.resetTraineePassword(row.id)
    toast.add({
      severity: 'warn',
      summary: 'Temporary password reset',
      detail: `${res.email}: ${res.temporaryPassword}`,
      life: 7000,
    })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Reset failed'
  }
}

async function openAssignDialog(row: TraineeResponse): Promise<void> {
  assignTrainee.value = row
  assignMode.value = 'assign'
  await loadCurricula()
  assignDialogVisible.value = true
  const grouped = groupCurriculaByFamily(curricula.value)
  if (grouped.length > 0) {
    assignFamilyGroupId.value = grouped[0].representative.curriculumGroupId
  } else {
    assignFamilyGroupId.value = null
    assignVersionId.value = null
  }
}

function closeAssignDialog(): void {
  assignDialogVisible.value = false
  assignMode.value = 'assign'
  assignFamilyGroupId.value = null
  assignVersionId.value = null
}

async function confirmAssign(): Promise<void> {
  const curriculum = assignCurriculumResolved.value
  if (!assignTrainee.value || !curriculum) return
  error.value = ''
  try {
    if (assignMode.value === 'replace') {
      await mentorApi.replaceActiveAssignment(assignTrainee.value.id, curriculum.id)
    } else {
      await mentorApi.assignCurriculum(assignTrainee.value.id, curriculum.id)
    }
    assignDialogVisible.value = false
    const actionLabel = assignMode.value === 'replace' ? 'Curriculum replaced' : 'Curriculum assigned'
    const actionVerb = assignMode.value === 'replace' ? 'replaced for' : 'assigned to'
    assignMode.value = 'assign'
    toast.add({
      severity: 'success',
      summary: actionLabel,
      detail: `${curriculum.name} (${curriculum.versionLabel}) ${actionVerb} ${assignTrainee.value.fullName}.`,
      life: 3500,
    })
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 409 && assignMode.value === 'assign') {
      error.value = ''
      assignMode.value = 'replace'
      toast.add({
        severity: 'warn',
        summary: 'Active assignment exists',
        detail: 'Confirm Replace to cancel current assignment and apply the selected curriculum.',
        life: 4500,
      })
      return
    }
    error.value = e instanceof ApiError ? e.message : 'Assign failed'
  }
}

function showBulkComingSoon(): void {
  toast.add({
    severity: 'info',
    summary: 'Coming soon',
    detail: 'Bulk actions are UI-only for now.',
    life: 3000,
  })
}

function openActionMenu(event: Event, row: TraineeResponse): void {
  actionMenuItems.value = [
    {
      label: row.active ? 'Deactivate' : 'Activate',
      icon: row.active ? 'pi pi-pause' : 'pi pi-play',
      command: () => {
        void toggleActive(row)
      },
    },
    {
      label: 'Reset password',
      icon: 'pi pi-key',
      command: () => {
        void resetPassword(row)
      },
    },
    {
      label: 'Assign curriculum',
      icon: 'pi pi-book',
      command: () => openAssignDialog(row),
    },
  ]
  actionMenu.value?.toggle(event)
}

function onPageChange(event: { first: number; rows: number }): void {
  first.value = event.first
  pageRows.value = event.rows
  void load()
}
</script>

<template>
  <div class="trainees-page">
    <Toast position="bottom-right" />

    <PageHeader title="Trainee management" description="Manage your trainees and their progress." />

    <section class="table-shell">
      <div class="table-top">
        <h2>All users <span>{{ summaryCount }}</span></h2>
        <div class="toolbar">
          <IconField>
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" placeholder="Search" @keyup.enter="search" />
          </IconField>
          <Select
            v-model="activeFilter"
            :options="activeFilterOptions"
            option-label="label"
            option-value="value"
            placeholder="Access status"
            class="active-filter"
          />
          <Button icon="pi pi-search" label="Search" severity="secondary" outlined @click="search" />
          <Button icon="pi pi-times" label="Clear" severity="secondary" outlined @click="resetFilters" />
          <Button icon="pi pi-users" label="Bulk actions" severity="secondary" outlined @click="showBulkComingSoon" />
          <Button icon="pi pi-plus" label="Add trainee" @click="createDialogVisible = true" />
        </div>
      </div>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DataTable
        :value="rows"
        data-key="id"
        :loading="loading"
        class="p-datatable-sm"
        responsive-layout="scroll"
      >
        <Column header="User" style="min-width: 16rem">
          <template #body="{ data }">
            <div class="user-cell">
              <Avatar :label="initials(data.fullName)" shape="circle" />
              <div>
                <p class="name">{{ data.fullName }}</p>
                <p class="email">{{ data.email }}</p>
              </div>
            </div>
          </template>
        </Column>

        <Column header="Access" style="min-width: 12rem">
          <template #body="{ data }">
            <div class="tag-row">
              <Tag value="Trainee" severity="info" rounded />
              <Tag :value="data.active ? 'Active' : 'Inactive'" :severity="data.active ? 'success' : 'danger'" rounded />
            </div>
          </template>
        </Column>

        <Column header="Last active" style="min-width: 9rem">
          <template #body="{ data }">
            {{ formatDate(data.createdAt) }}
          </template>
        </Column>

        <Column header="Date added" style="min-width: 9rem">
          <template #body="{ data }">
            {{ formatDate(data.createdAt) }}
          </template>
        </Column>

        <Column header-style="width: 4rem">
          <template #body="{ data }">
            <Button
              icon="pi pi-ellipsis-v"
              text
              rounded
              severity="secondary"
              @click="openActionMenu($event, data)"
            />
          </template>
        </Column>
      </DataTable>

      <div class="table-footer">
        <Paginator
          :rows="pageRows"
          :first="first"
          :total-records="totalRecords"
          :rows-per-page-options="[8, 12, 20]"
          template="PrevPageLink PageLinks NextPageLink RowsPerPageDropdown"
          @page="onPageChange"
        />
      </div>
    </section>

    <Dialog v-model:visible="createDialogVisible" modal header="Add trainee" :style="{ width: '28rem' }">
      <div class="dialog-fields">
        <label>
          Email
          <InputText v-model="createEmail" type="email" />
        </label>
        <label>
          Full name
          <InputText v-model="createName" />
        </label>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="createDialogVisible = false" />
        <Button label="Create" @click="createTrainee" :disabled="!createEmail || !createName" />
      </template>
    </Dialog>

    <Dialog v-model:visible="assignDialogVisible" modal header="Assign curriculum" :style="{ width: '32rem' }">
      <div class="dialog-fields">
        <p class="muted">
          Assign curriculum for <strong>{{ assignTrainee?.fullName }}</strong>
        </p>
        <Message v-if="assignMode === 'replace'" severity="warn" :closable="false">
          This trainee already has an active assignment. Click <strong>Replace</strong> to cancel the current one and
          regenerate tasks from the selected curriculum.
        </Message>
        <label class="assign-field">
          Curriculum
          <Select
            v-model="assignFamilyGroupId"
            :options="assignFamilyOptions"
            option-label="label"
            option-value="value"
            placeholder="Select curriculum"
            class="w-full"
          />
        </label>
        <label class="assign-field">
          Version
          <Select
            v-model="assignVersionId"
            :options="assignVersionOptions"
            option-label="label"
            option-value="value"
            placeholder="Select published version"
            class="w-full"
            :disabled="assignFamilyGroupId == null || assignVersionOptions.length === 0"
          />
        </label>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="closeAssignDialog" />
        <Button
          :label="assignMode === 'replace' ? 'Replace' : 'Assign'"
          :severity="assignMode === 'replace' ? 'warn' : undefined"
          @click="confirmAssign"
          :disabled="!assignCurriculumResolved || !assignTrainee"
        />
      </template>
    </Dialog>

    <Menu ref="actionMenu" :model="actionMenuItems" :popup="true" />
  </div>
</template>

<style scoped>
.trainees-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.table-shell {
  background: #fff;
  border: 1px solid var(--brand-border-soft);
  border-radius: 12px;
  padding: 1rem;
}

.table-top {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.table-top h2 {
  margin: 0;
  font-size: 1.15rem;
}

.table-top h2 span {
  color: var(--text-muted);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.active-filter {
  min-width: 11rem;
}

.table-footer {
  margin-top: 0.6rem;
  display: flex;
  justify-content: flex-end;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.name {
  margin: 0;
  font-weight: 600;
}

.email {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.86rem;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.dialog-fields {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.dialog-fields label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.muted {
  margin: 0;
  color: var(--text-muted);
}

.assign-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

@media (max-width: 900px) {
  .table-footer {
    justify-content: center;
  }
}
</style>
