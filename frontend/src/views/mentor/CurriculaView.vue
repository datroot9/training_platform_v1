<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type { CurriculumResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

type StatusFilter = 'ALL' | 'DRAFT' | 'PUBLISHED'

const router = useRouter()
const toast = useToast()

const rows = ref<CurriculumResponse[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const error = ref('')
const query = ref('')
const statusFilter = ref<StatusFilter>('ALL')
const first = ref(0)
const pageRows = ref(8)

const createDialogVisible = ref(false)
const createName = ref('')
const createDescription = ref('')
const creating = ref(false)
const rowPendingId = ref<number | null>(null)

const statusOptions: Array<{ label: string; value: StatusFilter }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Published', value: 'PUBLISHED' },
]

const summaryCount = computed(() => totalRecords.value)

function formatDate(value?: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

function statusSeverity(status: string): 'success' | 'warn' | 'contrast' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DRAFT') return 'warn'
  return 'contrast'
}

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const page = Math.floor(first.value / pageRows.value)
    const res = await mentorApi.listCurricula({
      q: query.value,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      page,
      size: pageRows.value,
      sortBy: 'updatedAt',
      sortDir: 'desc',
    })
    rows.value = res.items
    totalRecords.value = res.totalElements

    // If filters reduce total while user is on a later page, move back to the last valid page.
    if (res.items.length === 0 && res.totalElements > 0 && first.value >= res.totalElements) {
      const lastFirst = Math.max(0, (Math.ceil(res.totalElements / pageRows.value) - 1) * pageRows.value)
      if (lastFirst !== first.value) {
        first.value = lastFirst
        await load()
      }
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curricula'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(statusFilter, () => {
  first.value = 0
  void load()
})

watch(query, () => {
  if (!query.value.trim()) {
    first.value = 0
    void load()
  }
})

async function search(): Promise<void> {
  first.value = 0
  await load()
}

async function resetFilters(): Promise<void> {
  query.value = ''
  statusFilter.value = 'ALL'
  first.value = 0
  await load()
}

function openCreateDialog(): void {
  createName.value = ''
  createDescription.value = ''
  createDialogVisible.value = true
}

async function createCurriculum(): Promise<void> {
  error.value = ''
  creating.value = true
  try {
    await mentorApi.createCurriculum({
      name: createName.value.trim(),
      description: createDescription.value.trim(),
    })
    createDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Curriculum created',
      detail: 'New curriculum is ready in draft mode.',
      life: 3000,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Create failed'
  } finally {
    creating.value = false
  }
}

async function publishCurriculum(row: CurriculumResponse): Promise<void> {
  if (row.status !== 'DRAFT') return
  rowPendingId.value = row.id
  error.value = ''
  try {
    await mentorApi.publishCurriculum(row.id)
    toast.add({
      severity: 'success',
      summary: 'Curriculum published',
      detail: `${row.name} is now available for assignments.`,
      life: 3500,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publish failed'
  } finally {
    rowPendingId.value = null
  }
}

function goDetail(row: CurriculumResponse): void {
  void router.push(`/mentor/curricula/${row.id}`)
}

function onPageChange(event: { first: number; rows: number }): void {
  first.value = event.first
  pageRows.value = event.rows
  void load()
}
</script>

<template>
  <div class="curricula-page">
    <Toast position="bottom-right" />

    <PageHeader
      title="Curriculum management"
      description="Create and maintain curricula before assigning to trainees."
    />

    <section class="table-shell">
      <div class="table-tools">
        <div class="table-tools-left">
          <IconField>
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" placeholder="Search by name or description" @keyup.enter="search" />
          </IconField>
          <Select
            v-model="statusFilter"
            :options="statusOptions"
            option-label="label"
            option-value="value"
            class="status-filter"
          />
          <Button icon="pi pi-search" label="Search" severity="secondary" outlined @click="search" />
          <Button icon="pi pi-times" label="Clear" severity="secondary" outlined @click="resetFilters" />
        </div>
        <Button icon="pi pi-plus" label="Create curriculum" @click="openCreateDialog" />
      </div>
      <p class="table-count">All curricula: <strong>{{ summaryCount }}</strong></p>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DataTable
        :value="rows"
        data-key="id"
        :loading="loading"
        class="p-datatable-sm"
        responsive-layout="scroll"
        sort-field="updatedAt"
        :sort-order="-1"
      >
        <Column field="name" header="Curriculum" style="min-width: 14rem">
          <template #body="{ data }">
            <div>
              <p class="name">{{ data.name }}</p>
            </div>
          </template>
        </Column>

        <Column header="Status" style="width: 8rem">
          <template #body="{ data }">
            <Tag :value="data.status" :severity="statusSeverity(data.status)" rounded />
          </template>
        </Column>

        <Column header="Updated" style="width: 9rem">
          <template #body="{ data }">
            {{ formatDate(data.updatedAt) }}
          </template>
        </Column>

        <Column header-style="width: 14rem">
          <template #body="{ data }">
            <div class="actions">
              <Button label="View detail" text size="small" @click="goDetail(data)" />
              <Button
                v-if="data.status === 'DRAFT'"
                label="Publish"
                severity="success"
                size="small"
                outlined
                :loading="rowPendingId === data.id"
                @click="publishCurriculum(data)"
              />
            </div>
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

    <Dialog v-model:visible="createDialogVisible" modal header="Create curriculum" :style="{ width: '32rem' }">
      <div class="dialog-form">
        <label>
          Name
          <InputText v-model="createName" placeholder="e.g. Java Core Bootcamp" />
        </label>
        <label>
          Description
          <Textarea v-model="createDescription" rows="4" auto-resize />
        </label>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="createDialogVisible = false" />
        <Button label="Create" :loading="creating" :disabled="!createName.trim()" @click="createCurriculum" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.curricula-page {
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

.table-tools {
  margin-bottom: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: space-between;
  align-items: center;
}

.table-tools-left {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.status-filter {
  min-width: 12rem;
}

.table-count {
  margin: 0 0 0.75rem;
  color: var(--text-muted);
}

.name {
  margin: 0;
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 0.35rem;
  justify-content: flex-end;
}

.table-footer {
  margin-top: 0.6rem;
  display: flex;
  justify-content: flex-end;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.dialog-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

@media (max-width: 900px) {
  .table-tools {
    align-items: stretch;
  }

  .table-tools-left {
    width: 100%;
  }

  .table-tools :deep(.p-button) {
    width: 100%;
  }

  .table-footer {
    justify-content: center;
  }

}
</style>
