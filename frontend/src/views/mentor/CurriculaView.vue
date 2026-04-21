<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Paginator from 'primevue/paginator'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import PageHeader from '../../components/layout/PageHeader.vue'
import { groupCurriculaByFamily, type CurriculumGroupRow } from '../../utils/curriculumGroups'

type StatusFilter = 'ALL' | 'DRAFT' | 'PUBLISHED'

const router = useRouter()
const toast = useToast()

const allGrouped = ref<CurriculumGroupRow[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const error = ref('')
const query = ref('')
const statusFilter = ref<StatusFilter>('ALL')
const first = ref(0)
const pageRows = ref(8)

const rowPendingId = ref<number | null>(null)

const statusOptions: Array<{ label: string; value: StatusFilter }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Published', value: 'PUBLISHED' },
]

const summaryCount = computed(() => totalRecords.value)

const paginatedGroups = computed(() => {
  const start = first.value
  return allGrouped.value.slice(start, start + pageRows.value)
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
    const items = await mentorApi.listAllCurricula({
      q: query.value.trim() || undefined,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      sortBy: 'updatedAt',
      sortDir: 'desc',
      size: 100,
    })
    const grouped = groupCurriculaByFamily(items)
    allGrouped.value = grouped
    totalRecords.value = grouped.length

    if (grouped.length > 0 && first.value >= grouped.length) {
      const lastFirst = Math.max(0, (Math.ceil(grouped.length / pageRows.value) - 1) * pageRows.value)
      if (lastFirst !== first.value) {
        first.value = lastFirst
      }
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curricula'
    allGrouped.value = []
    totalRecords.value = 0
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

async function publishCurriculum(row: CurriculumGroupRow): Promise<void> {
  const r = row.representative
  if (r.status !== 'DRAFT') return
  rowPendingId.value = r.id
  error.value = ''
  try {
    await mentorApi.publishCurriculum(r.id)
    toast.add({
      severity: 'success',
      summary: 'Curriculum published',
      detail: `${r.name} is now available for assignments.`,
      life: 3500,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publish failed'
  } finally {
    rowPendingId.value = null
  }
}

function goDetail(row: CurriculumGroupRow): void {
  void router.push(`/mentor/curricula/${row.representative.id}`)
}

/** Primary create action: multi-step wizard (basics → PDFs → templates → publish). */
function createCurriculum(): void {
  void router.push({ name: 'mentor-curriculum-wizard' })
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
        <Button icon="pi pi-plus" label="Create curriculum" @click="createCurriculum" />
      </div>
      <p class="table-count">
        Curriculum families: <strong>{{ summaryCount }}</strong>
        <span class="table-count-hint"> (latest version per family)</span>
      </p>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DataTable
        :value="paginatedGroups"
        data-key="representative.id"
        :loading="loading"
        class="p-datatable-sm"
        responsive-layout="scroll"
      >
        <Column header="Curriculum" style="min-width: 14rem">
          <template #body="{ data }">
            <div>
              <p class="name">{{ data.representative.name }}</p>
              <p v-if="data.versionCount > 1" class="family-meta">
                {{ data.versionCount }} versions · showing newest
              </p>
            </div>
          </template>
        </Column>

        <Column header="Latest version" style="width: 9rem">
          <template #body="{ data }">
            <span class="version-cell">{{ data.representative.versionLabel }}</span>
          </template>
        </Column>

        <Column header="Status" style="width: 8rem">
          <template #body="{ data }">
            <Tag
              :value="data.representative.status"
              :severity="statusSeverity(data.representative.status)"
              rounded
            />
          </template>
        </Column>

        <Column header="Updated" style="width: 9rem">
          <template #body="{ data }">
            {{ formatDate(data.representative.updatedAt) }}
          </template>
        </Column>

        <Column header-style="width: 14rem">
          <template #body="{ data }">
            <div class="actions">
              <Button label="View detail" text size="small" @click="goDetail(data)" />
              <Button
                v-if="data.representative.status === 'DRAFT'"
                label="Publish"
                severity="success"
                size="small"
                outlined
                :loading="rowPendingId === data.representative.id"
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
  </div>
</template>

<style scoped>
.curricula-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.table-shell {
  background: var(--ui-surface);
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-md);
  padding: 1.25rem;
  box-shadow: var(--ui-shadow-sm);
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

.table-count-hint {
  font-size: 0.85rem;
  font-weight: normal;
}

.family-meta {
  margin: 0.2rem 0 0;
  font-size: 0.8rem;
  color: var(--text-muted);
  font-weight: normal;
}

.name {
  margin: 0;
  font-weight: 600;
}

.version-cell {
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
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
