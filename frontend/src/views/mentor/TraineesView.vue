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

const toast = useToast()

const query = ref('')
const rows = ref<TraineeResponse[]>([])
const curricula = ref<CurriculumResponse[]>([])
const loading = ref(false)
const error = ref('')

const createDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const createEmail = ref('')
const createName = ref('')
const assignTrainee = ref<TraineeResponse | null>(null)
const assignCurriculum = ref<CurriculumResponse | null>(null)

const first = ref(0)
const pageRows = ref(8)
const actionMenu = ref()
const actionMenuItems = ref<MenuItem[]>([])

const pagedRows = computed(() => rows.value.slice(first.value, first.value + pageRows.value))

const summaryCount = computed(() => rows.value.length)

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
    rows.value = await mentorApi.listTrainees(query.value)
    if (first.value >= rows.value.length) {
      goToFirstPage()
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load trainees'
  } finally {
    loading.value = false
  }
}

async function loadCurricula(): Promise<void> {
  try {
    curricula.value = await mentorApi.listCurricula()
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
    void load()
  }
})

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

function openAssignDialog(row: TraineeResponse): void {
  assignTrainee.value = row
  assignCurriculum.value = curricula.value[0] ?? null
  assignDialogVisible.value = true
}

async function confirmAssign(): Promise<void> {
  if (!assignTrainee.value || !assignCurriculum.value) return
  error.value = ''
  try {
    await mentorApi.assignCurriculum(assignTrainee.value.id, assignCurriculum.value.id)
    assignDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Curriculum assigned',
      detail: `${assignCurriculum.value.name} assigned to ${assignTrainee.value.fullName}.`,
      life: 3500,
    })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Assign failed'
  }
}

function showFilterComingSoon(): void {
  toast.add({
    severity: 'info',
    summary: 'Coming soon',
    detail: 'Advanced filters are not yet supported by backend APIs.',
    life: 3000,
  })
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
}
</script>

<template>
  <div class="trainees-page">
    <Toast position="bottom-right" />

    <div class="header">
      <h1>Trainee management</h1>
      <p>Manage your trainees and their progress.</p>
    </div>

    <section class="table-shell">
      <div class="table-top">
        <h2>All users <span>{{ summaryCount }}</span></h2>
        <div class="toolbar">
          <IconField>
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" placeholder="Search" @keyup.enter="load" />
          </IconField>

          <Button
            icon="pi pi-filter"
            label="Filters"
            severity="secondary"
            outlined
            @click="showFilterComingSoon"
          />
          <Button icon="pi pi-users" label="Bulk actions" severity="secondary" outlined @click="showBulkComingSoon" />
          <Button icon="pi pi-plus" label="Add user" @click="createDialogVisible = true" />
        </div>
      </div>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DataTable
        :value="pagedRows"
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
          :total-records="rows.length"
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

    <Dialog v-model:visible="assignDialogVisible" modal header="Assign curriculum" :style="{ width: '28rem' }">
      <div class="dialog-fields">
        <p class="muted">
          Assign curriculum for <strong>{{ assignTrainee?.fullName }}</strong>
        </p>
        <Select
          v-model="assignCurriculum"
          :options="curricula"
          option-label="name"
          placeholder="Select curriculum"
          class="w-full"
        />
      </div>
      <template #footer>
        <Button label="Cancel" text @click="assignDialogVisible = false" />
        <Button label="Assign" @click="confirmAssign" :disabled="!assignCurriculum || !assignTrainee" />
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

.header h1 {
  margin: 0;
  font-size: 1.9rem;
}

.header p {
  margin: 0.3rem 0 0;
  color: #64748b;
}

.table-shell {
  background: #fff;
  border: 1px solid #e2e8f0;
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
  color: #64748b;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
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
  color: #64748b;
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
  color: #64748b;
}

@media (max-width: 900px) {
  .table-footer {
    justify-content: center;
  }
}
</style>
