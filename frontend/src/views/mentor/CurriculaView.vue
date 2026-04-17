<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
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
const loading = ref(false)
const error = ref('')
const query = ref('')
const statusFilter = ref<StatusFilter>('ALL')

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

const filteredRows = computed(() => {
  const q = query.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const matchStatus = statusFilter.value === 'ALL' || item.status === statusFilter.value
    const matchQuery =
      !q ||
      item.name.toLowerCase().includes(q) ||
      (item.description ?? '').toLowerCase().includes(q) ||
      item.status.toLowerCase().includes(q)
    return matchStatus && matchQuery
  })
})

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
    rows.value = await mentorApi.listCurricula()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curricula'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

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
            <InputText v-model="query" placeholder="Search by name or description" />
          </IconField>
          <Select
            v-model="statusFilter"
            :options="statusOptions"
            option-label="label"
            option-value="value"
            class="status-filter"
          />
        </div>
        <Button icon="pi pi-plus" label="Create curriculum" @click="openCreateDialog" />
      </div>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DataTable
        :value="filteredRows"
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

.name {
  margin: 0;
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 0.35rem;
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

}
</style>
