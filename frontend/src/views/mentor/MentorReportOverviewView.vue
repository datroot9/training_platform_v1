<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import { useMediaQuery } from '../../composables/useMediaQuery'
import type { DailyReportResponse, TraineeResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

const query = ref('')
const trainees = ref<TraineeResponse[]>([])
const traineesLoading = ref(false)
const traineesError = ref('')
type DailyRangePreset = 'LAST_10_DAYS' | 'LAST_1_MONTH' | 'CUSTOM'
const dailyRangePreset = ref<DailyRangePreset>('LAST_10_DAYS')
const customFromDate = ref('')
const customToDate = ref('')
const rangeValidationError = ref('')
type InboxDailyReport = DailyReportResponse & {
  traineeId: number
  traineeFullName: string
  traineeEmail: string
  assignmentName: string
}
type DailyReportGroup = {
  reportDate: string
  items: InboxDailyReport[]
  count: number
}
const dailyReports = ref<InboxDailyReport[]>([])
const dailyLoading = ref(false)
const dailyError = ref('')
const dailyDetailVisible = ref(false)
const dailyDetailItem = ref<InboxDailyReport | null>(null)
const expandedGroupDates = ref<string[]>([])
let dailyRequestVersion = 0

const hasSearchQuery = computed(() => query.value.trim().length > 0)
const isMobileTable = useMediaQuery('(max-width: 900px)')
const currentDailyRangeLabel = computed(() => {
  if (dailyRangePreset.value === 'LAST_10_DAYS') return 'last 10 days'
  if (dailyRangePreset.value === 'LAST_1_MONTH') return 'last 1 month'
  if (!customFromDate.value || !customToDate.value) return 'custom range'
  return `${customFromDate.value} to ${customToDate.value}`
})
const filteredDailyReports = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return dailyReports.value
  return dailyReports.value.filter(
    (item) =>
      item.traineeFullName.toLowerCase().includes(q) ||
      item.traineeEmail.toLowerCase().includes(q) ||
      item.fresherLabel.toLowerCase().includes(q),
  )
})
const dailyReportGroups = computed<DailyReportGroup[]>(() => {
  const groups = new Map<string, InboxDailyReport[]>()
  for (const item of filteredDailyReports.value) {
    const rows = groups.get(item.reportDate)
    if (rows) {
      rows.push(item)
    } else {
      groups.set(item.reportDate, [item])
    }
  }
  return Array.from(groups.entries()).map(([reportDate, items]) => ({
    reportDate,
    items,
    count: items.length,
  }))
})

function isGroupExpanded(dateKey: string): boolean {
  return expandedGroupDates.value.includes(dateKey)
}

function toggleGroup(dateKey: string): void {
  if (isGroupExpanded(dateKey)) {
    expandedGroupDates.value = expandedGroupDates.value.filter((key) => key !== dateKey)
    return
  }
  expandedGroupDates.value = [...expandedGroupDates.value, dateKey]
}

function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}

function toIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function presetRange(preset: Exclude<DailyRangePreset, 'CUSTOM'>): { fromDate: string; toDate: string } {
  const today = new Date()
  const toDate = toIsoDate(today)
  if (preset === 'LAST_10_DAYS') {
    const from = new Date(today)
    from.setDate(from.getDate() - 9)
    return { fromDate: toIsoDate(from), toDate }
  }
  const from = new Date(today)
  from.setMonth(from.getMonth() - 1)
  return { fromDate: toIsoDate(from), toDate }
}

function syncCustomRangeFromPreset(preset: Exclude<DailyRangePreset, 'CUSTOM'>): void {
  const range = presetRange(preset)
  customFromDate.value = range.fromDate
  customToDate.value = range.toDate
}

function applyPreset(preset: Exclude<DailyRangePreset, 'CUSTOM'>): void {
  dailyRangePreset.value = preset
  syncCustomRangeFromPreset(preset)
}

function onCustomRangeChanged(): void {
  dailyRangePreset.value = 'CUSTOM'
}

function buildDevSampleDailyReports(): InboxDailyReport[] {
  const now = new Date()
  const oneDayMs = 24 * 60 * 60 * 1000
  const buildDate = (daysAgo: number) => toIsoDate(new Date(now.getTime() - daysAgo * oneDayMs))
  const buildDateTime = (daysAgo: number, hour: number, minute: number) => {
    const d = new Date(now.getTime() - daysAgo * oneDayMs)
    d.setHours(hour, minute, 0, 0)
    return d.toISOString()
  }

  const samples: InboxDailyReport[] = Array.from({ length: 20 }, (_, index) => {
    const isEven = index % 2 === 0
    const reportDate = buildDate(index)
    return {
      id: 900001 + index,
      assignmentId: isEven ? 7001 : 7002,
      reportDate,
      status: 'SUBMITTED' as const,
      fresherLabel: isEven ? 'Dat123' : 'Giang',
      trainingDayIndex: 6 + index,
      whatDone: `Completed mentor inbox story slice #${index + 1} and validated reporting UX consistency.`,
      plannedTomorrow: `Continue with backend cleanup and tests for report item #${index + 2}.`,
      blockers: index % 5 === 0 ? 'Need mentor confirmation on endpoint naming.' : 'No blockers',
      resources:
        index % 3 === 0
          ? [
              {
                id: 910001 + index,
                type: 'GITHUB',
                label: `Demo PR #${index + 100}`,
                url: `https://github.com/example/training-platform/pull/${index + 100}`,
              },
            ]
          : [],
      submittedAt: buildDateTime(index, 17 + (index % 2), 10 + (index % 40)),
      reviewedAt: null,
      taskHours: [],
      traineeId: isEven ? 5001 : 5002,
      traineeFullName: isEven ? 'Huynh Dat' : 'Cao Thuy Giang',
      traineeEmail: isEven ? 'dat123@gmail.com' : 'giangct@gmail.com',
      assignmentName: 'Backend API Fundamentals',
    }
  })
  return samples.sort((a, b) => {
    const byDate = b.reportDate.localeCompare(a.reportDate)
    if (byDate !== 0) return byDate
    return (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '')
  })
}

function calculateDailyRange(preset: DailyRangePreset): { fromDate: string; toDate: string } {
  if (preset === 'LAST_10_DAYS') return presetRange('LAST_10_DAYS')
  if (preset === 'LAST_1_MONTH') return presetRange('LAST_1_MONTH')
  return { fromDate: customFromDate.value, toDate: customToDate.value }
}

async function loadTrainees(): Promise<void> {
  traineesLoading.value = true
  traineesError.value = ''
  try {
    const res = await mentorApi.listActiveTrainees({
      page: 0,
      size: 100,
      sortBy: 'fullName',
      sortDir: 'asc',
    })
    trainees.value = res.items
  } catch (e) {
    traineesError.value = e instanceof ApiError ? e.message : 'Failed to load active trainees'
  } finally {
    traineesLoading.value = false
  }
}

async function loadDailyReports(): Promise<void> {
  if (dailyRangePreset.value === 'CUSTOM') {
    if (!customFromDate.value || !customToDate.value) {
      rangeValidationError.value = 'Please select both from and to dates.'
      return
    }
    if (customFromDate.value > customToDate.value) {
      rangeValidationError.value = 'From date must be on or before to date.'
      return
    }
  }
  rangeValidationError.value = ''

  dailyRequestVersion += 1
  const requestVersion = dailyRequestVersion
  dailyReports.value = []
  dailyError.value = ''
  if (trainees.value.length === 0) {
    if (import.meta.env.DEV) {
      dailyReports.value = buildDevSampleDailyReports()
    }
    dailyLoading.value = false
    return
  }

  dailyLoading.value = true
  try {
    const allReports = await Promise.all(
      trainees.value.map(async (trainee) => {
        const assignment = await mentorApi.getTraineeActiveAssignmentOrNull(trainee.id)
        if (!assignment) return [] as InboxDailyReport[]
        const range = calculateDailyRange(dailyRangePreset.value)
        const reports = await mentorApi.listDailyReportsForTraineeByRange(
          trainee.id,
          assignment.id,
          range.fromDate,
          range.toDate,
        )
        return reports.map(
          (item): InboxDailyReport => ({
            ...item,
            traineeId: trainee.id,
            traineeFullName: trainee.fullName,
            traineeEmail: trainee.email,
            assignmentName: assignment.curriculumName,
          }),
        )
      }),
    )
    if (requestVersion !== dailyRequestVersion) return
    const mergedReports = allReports
      .flat()
      .sort((a, b) => {
        const byDate = b.reportDate.localeCompare(a.reportDate)
        if (byDate !== 0) return byDate
        return (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '')
      })
    const submittedReports = mergedReports.filter((item) => item.status === 'SUBMITTED')
    if (import.meta.env.DEV && submittedReports.length === 0) {
      dailyReports.value = buildDevSampleDailyReports()
      return
    }
    dailyReports.value = submittedReports
  } catch (e) {
    if (requestVersion !== dailyRequestVersion) return
    if (import.meta.env.DEV) {
      dailyError.value = ''
      dailyReports.value = buildDevSampleDailyReports()
      return
    }
    dailyError.value = e instanceof ApiError ? e.message : 'Failed to load reports'
  } finally {
    if (requestVersion === dailyRequestVersion) {
      dailyLoading.value = false
    }
  }
}

function searchTrainees(): void {
  // Local filtering only in inbox mode.
}

function clearSearch(): void {
  query.value = ''
}

function openDailyDetail(item: InboxDailyReport): void {
  dailyDetailItem.value = item
  dailyDetailVisible.value = true
}

function closeDailyDetail(): void {
  dailyDetailVisible.value = false
  dailyDetailItem.value = null
}

onMounted(async () => {
  syncCustomRangeFromPreset('LAST_10_DAYS')
  await loadTrainees()
  await loadDailyReports()
})

watch([dailyRangePreset, trainees], () => {
  void loadDailyReports()
})

watch([customFromDate, customToDate], () => {
  if (dailyRangePreset.value === 'CUSTOM') {
    void loadDailyReports()
  }
})

watch(dailyReportGroups, (groups) => {
  const valid = new Set(groups.map((group) => group.reportDate))
  expandedGroupDates.value = expandedGroupDates.value.filter((key) => valid.has(key))
})
</script>

<template>
  <div class="mentor-reports-page">
    <PageHeader title="Mentor reports" description="Inbox view of daily reports across active trainees and assignments." />

    <section class="reports-layout">
      <section class="report-panel card-shell">
        <div class="panel-tools">
          <IconField class="search-field">
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" placeholder="Search trainee or email" @keyup.enter="searchTrainees" />
            <button
              v-if="hasSearchQuery"
              type="button"
              class="clear-query-btn"
              aria-label="Clear search"
              @click="clearSearch"
            >
              <i class="pi pi-times" />
            </button>
          </IconField>
        </div>

        <article class="surface-card">
          <div class="card-head">
            <h4>Daily reports</h4>
            <div class="filter-controls">
              <div class="daily-presets">
                <Button
                  label="10 days"
                  size="small"
                  text
                  :class="{ 'is-active': dailyRangePreset === 'LAST_10_DAYS' }"
                  @click="applyPreset('LAST_10_DAYS')"
                />
                <Button
                  label="1 month"
                  size="small"
                  text
                  :class="{ 'is-active': dailyRangePreset === 'LAST_1_MONTH' }"
                  @click="applyPreset('LAST_1_MONTH')"
                />
              </div>
              <div class="range-inputs">
                <label class="range-field">
                  <span>From</span>
                  <input v-model="customFromDate" type="date" @change="onCustomRangeChanged" />
                </label>
                <label class="range-field">
                  <span>To</span>
                  <input v-model="customToDate" type="date" @change="onCustomRangeChanged" />
                </label>
              </div>
            </div>
          </div>

          <p class="muted small">Showing {{ currentDailyRangeLabel }}.</p>
          <Message v-if="rangeValidationError" severity="warn" :closable="false">{{ rangeValidationError }}</Message>

          <Message v-if="traineesError" severity="error" :closable="false">{{ traineesError }}</Message>
          <p v-else-if="traineesLoading || dailyLoading" class="muted small">Loading daily reports...</p>
          <Message v-else-if="dailyError" severity="error" :closable="false">{{ dailyError }}</Message>
          <p v-else-if="dailyReportGroups.length === 0" class="muted small">No daily reports found for this range.</p>
          <ul v-else class="day-group-list" :class="{ 'day-group-list--mobile': isMobileTable }">
            <li
              v-for="group in dailyReportGroups"
              :key="group.reportDate"
              class="day-group-card"
              :class="{ 'day-group-card--active': isGroupExpanded(group.reportDate) }"
            >
              <button
                type="button"
                class="day-group-header"
                :class="{ 'day-group-header--expanded': isGroupExpanded(group.reportDate) }"
                @click="toggleGroup(group.reportDate)"
              >
                <div class="day-group-title">
                  <i class="pi pi-envelope day-group-icon" />
                  <strong>{{ group.reportDate }}</strong>
                </div>
                <div class="day-group-meta">
                  <span class="day-group-count">{{ group.count }} report{{ group.count > 1 ? 's' : '' }}</span>
                  <i
                    class="pi pi-chevron-down day-group-chevron"
                    :class="{ 'day-group-chevron--expanded': isGroupExpanded(group.reportDate) }"
                  />
                </div>
              </button>
              <div v-if="isGroupExpanded(group.reportDate)" class="day-group-body">
                <ul class="daily-list">
                  <li v-for="item in group.items" :key="item.id" class="daily-row">
                    <div class="daily-row-head">
                      <strong>{{ item.traineeFullName }}</strong>
                      <div class="daily-row-actions">
                        <Tag :value="item.status" :severity="item.status === 'SUBMITTED' ? 'info' : 'secondary'" rounded />
                        <Button label="View details" size="small" text @click="openDailyDetail(item)" />
                      </div>
                    </div>
                    <p class="daily-row-meta">
                      <span class="daily-meta-chip">{{ item.traineeEmail }}</span>
                    </p>
                    <p class="daily-row-meta">
                      <span class="daily-meta-chip">{{ item.fresherLabel }} · Training day {{ item.trainingDayIndex }} · {{ item.assignmentName }}</span>
                    </p>
                  </li>
                </ul>
              </div>
            </li>
          </ul>
        </article>

      </section>
    </section>

    <Dialog
      v-model:visible="dailyDetailVisible"
      modal
      :draggable="false"
      :style="{ width: 'min(760px, 94vw)' }"
      :header="dailyDetailItem ? `Daily report · ${dailyDetailItem.reportDate}` : 'Daily report details'"
      class="daily-detail-dialog"
      @hide="closeDailyDetail"
    >
      <template v-if="dailyDetailItem">
        <div class="daily-detail-body">
          <section class="daily-detail-hero">
            <div class="daily-detail-identity">
              <p class="daily-detail-name">
                <i class="pi pi-user" />
                <span>{{ dailyDetailItem.fresherLabel }}</span>
              </p>
              <p class="daily-detail-meta">
                <i class="pi pi-calendar" />
                <span>Training day {{ dailyDetailItem.trainingDayIndex }}</span>
              </p>
            </div>
            <div class="daily-detail-status">
              <Tag
                :value="dailyDetailItem.status"
                :severity="dailyDetailItem.status === 'SUBMITTED' ? 'info' : 'secondary'"
                rounded
              />
              <p class="daily-detail-date">{{ dailyDetailItem.reportDate }}</p>
            </div>
          </section>

          <div class="detail-grid">
            <section class="detail-block detail-block--done">
              <h4>
                <i class="pi pi-check-circle" />
                <span>What done</span>
              </h4>
              <p>{{ dailyDetailItem.whatDone || 'No update.' }}</p>
            </section>

            <section class="detail-block detail-block--plan">
              <h4>
                <i class="pi pi-arrow-right" />
                <span>Planned tomorrow</span>
              </h4>
              <p>{{ dailyDetailItem.plannedTomorrow || 'No plan provided.' }}</p>
            </section>
          </div>

          <section class="detail-block detail-block--blockers">
            <h4>
              <i class="pi pi-exclamation-triangle" />
              <span>Blockers</span>
            </h4>
            <p>{{ dailyDetailItem.blockers || 'No blockers.' }}</p>
          </section>

          <section class="detail-block detail-block--resources">
            <h4>
              <i class="pi pi-link" />
              <span>Resources</span>
            </h4>
            <p v-if="dailyDetailItem.resources.length === 0" class="muted small">No resources attached.</p>
            <ul v-else class="resource-list">
              <li v-for="resource in dailyDetailItem.resources" :key="resource.id" class="resource-row">
                <div class="resource-main">
                  <Tag :value="resource.type" severity="secondary" />
                  <span>{{ resource.label || 'No label' }}</span>
                </div>
                <a :href="resource.url" target="_blank" rel="noopener noreferrer">{{ resource.url }}</a>
              </li>
            </ul>
          </section>

          <section class="detail-block detail-block--meta detail-meta-grid">
            <div>
              <h4>
                <i class="pi pi-send" />
                <span>Submitted at</span>
              </h4>
              <p>{{ formatDateTime(dailyDetailItem.submittedAt) }}</p>
            </div>
            <div>
              <h4>
                <i class="pi pi-verified" />
                <span>Reviewed at</span>
              </h4>
              <p>{{ formatDateTime(dailyDetailItem.reviewedAt) }}</p>
            </div>
          </section>
        </div>
      </template>

      <template #footer>
        <Button label="Close" @click="closeDailyDetail" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.mentor-reports-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.reports-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.card-shell {
  background: linear-gradient(165deg, #ffffff 0%, color-mix(in srgb, #ffffff 80%, var(--ui-accent-2-soft)) 100%);
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border));
  border-radius: var(--ui-radius-lg);
  box-shadow: 0 12px 24px -18px color-mix(in srgb, var(--ui-accent-2) 24%, transparent), var(--ui-shadow-md);
  padding: 1rem;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.55rem;
}

.panel-head h3,
.report-head h3 {
  margin: 0;
}

.panel-tools {
  display: block;
  margin-bottom: 0.65rem;
}

.search-field {
  position: relative;
  width: 100%;
}

.search-field :deep(.p-inputtext) {
  padding-right: 2rem;
  background: color-mix(in srgb, #ffffff 80%, var(--ui-accent-2-soft));
  border-color: color-mix(in srgb, var(--ui-accent-2) 20%, var(--ui-border));
  transition: border-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast),
    background-color var(--ui-transition-fast);
}

.search-field :deep(.p-inputtext:hover) {
  border-color: color-mix(in srgb, var(--ui-accent-2) 30%, var(--ui-border));
}

.search-field :deep(.p-inputtext:focus) {
  border-color: color-mix(in srgb, var(--ui-accent-2) 42%, var(--ui-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ui-accent-2-soft) 65%, transparent);
  background: #ffffff;
}

.clear-query-btn {
  position: absolute;
  top: 50%;
  right: 0.55rem;
  transform: translateY(-50%);
  border: 0;
  background: transparent;
  color: var(--ui-text-secondary);
  padding: 0;
  width: 1.1rem;
  height: 1.1rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.clear-query-btn:hover {
  color: var(--ui-text-primary);
}

.clear-query-btn:focus-visible {
  outline: 2px solid var(--ui-focus-ring);
  outline-offset: 2px;
  border-radius: 999px;
}

.trainee-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: min(72vh, 850px);
  overflow: auto;
}

.trainee-item {
  border: 1px solid var(--ui-border-soft);
  border-radius: 10px;
  background: var(--ui-surface);
  text-align: left;
  padding: 0.55rem 0.6rem;
  cursor: pointer;
  box-shadow: var(--ui-shadow-xs);
  transition: border-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast),
    transform var(--ui-transition-fast);
}

.trainee-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--ui-accent-2) 30%, var(--ui-border));
  box-shadow: var(--ui-shadow-sm);
}

.trainee-item.active {
  border-color: var(--ui-accent-2);
  box-shadow: 0 0 0 2px var(--ui-focus-ring), var(--ui-shadow-sm);
}

.trainee-item-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.5rem;
}

.trainee-user {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
}

.trainee-main {
  min-width: 0;
}

.name,
.email,
.meta {
  margin: 0;
}

.name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--ui-text-primary);
}

.email,
.meta {
  color: var(--ui-text-secondary);
  font-size: 0.8rem;
  line-height: 1.35;
}

.meta {
  margin-top: 0.28rem;
}

.report-panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  background: linear-gradient(180deg, color-mix(in srgb, #ffffff 82%, var(--ui-accent-2-soft)) 0%, #ffffff 100%);
}

.report-head {
  display: flex;
  justify-content: space-between;
  gap: 0.65rem;
  align-items: center;
  flex-wrap: wrap;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 12%, var(--ui-border));
  background: color-mix(in srgb, #ffffff 76%, var(--ui-accent-2-soft));
  border-radius: 12px;
  padding: 0.62rem 0.72rem;
}

.report-identity {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.report-head-main {
  min-width: 0;
}

.report-head-main h3 {
  font-size: 1.18rem;
  color: var(--ui-heading);
}

.report-email {
  margin: 0.15rem 0 0;
  font-size: 0.92rem;
  color: color-mix(in srgb, var(--ui-text-secondary) 92%, var(--ui-accent-deep));
  font-weight: 500;
}

.active-only-note {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: color-mix(in srgb, var(--ui-warn) 86%, var(--ui-accent-deep));
  padding: 0.3rem 0.58rem;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--ui-highlight) 54%, transparent);
  background: linear-gradient(135deg, var(--ui-highlight-soft), color-mix(in srgb, #ffffff 68%, var(--ui-highlight-soft)));
}

.assignment-section {
  border: 1px solid var(--ui-border);
  border-radius: 12px;
  padding: 0.85rem;
  background: var(--ui-surface-soft);
  box-shadow: var(--ui-shadow-xs);
}

.assignment-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.6rem;
  margin-bottom: 0.65rem;
}

.assignment-title,
.assignment-meta {
  margin: 0;
}

.assignment-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--ui-heading);
}

.assignment-meta {
  margin-top: 0.2rem;
  font-size: 0.82rem;
  color: var(--ui-text-secondary);
}

.report-mode-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.7rem;
}

.surface-card {
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border));
  border-radius: 10px;
  background: linear-gradient(180deg, #ffffff 0%, color-mix(in srgb, #ffffff 84%, var(--ui-accent-2-soft)) 100%);
  padding: 0.75rem;
  box-shadow: 0 10px 22px -20px color-mix(in srgb, var(--ui-accent-2) 25%, transparent), var(--ui-shadow-xs);
}

.surface-card h4 {
  margin: 0 0 0.45rem;
  font-size: 0.92rem;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
}

.filter-controls {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.week-select {
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  padding: 0.35rem 0.45rem;
  font-size: 0.82rem;
  min-width: 15rem;
  background: #ffffff;
  color: var(--ui-text-primary);
}

.daily-presets {
  display: inline-flex;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.daily-presets :deep(.p-button) {
  border-radius: 0;
}

.daily-presets :deep(.p-button.is-active) {
  background: var(--ui-accent-2-soft);
  color: var(--ui-accent-2);
}

.range-inputs {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.range-field {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  padding: 0.22rem 0.38rem;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 20%, var(--ui-border));
  border-radius: 8px;
  background: color-mix(in srgb, #ffffff 85%, var(--ui-accent-2-soft));
  font-size: 0.76rem;
  color: var(--ui-text-secondary);
}

.range-field span {
  font-weight: 600;
  color: var(--ui-accent-deep);
}

.range-field input {
  border: 0;
  background: transparent;
  color: var(--ui-text-primary);
  font-size: 0.8rem;
  padding: 0;
  min-width: 8.7rem;
  outline: none;
}

.range-field:focus-within {
  border-color: color-mix(in srgb, var(--ui-accent-2) 42%, var(--ui-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ui-accent-2-soft) 65%, transparent);
}

@media (max-width: 1280px) {
  .report-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .report-mode-tabs :deep(.p-button) {
    flex: 1 1 9rem;
  }

  .daily-presets {
    display: flex;
    border: 0;
    border-radius: 0;
    background: transparent;
    gap: 0.4rem;
    overflow: visible;
  }

  .daily-presets :deep(.p-button) {
    border-radius: 8px;
    border: 1px solid var(--ui-border);
    flex: 1 1 7.25rem;
  }

  .filter-controls {
    width: 100%;
  }

  .range-inputs {
    width: 100%;
    gap: 0.45rem;
  }

  .range-field {
    flex: 1 1 12rem;
  }

  .range-field input {
    min-width: 0;
    width: 100%;
  }
}

.daily-list,
.weekly-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.day-group-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.day-group-card {
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border));
  border-radius: 12px;
  background: linear-gradient(150deg, #ffffff 0%, color-mix(in srgb, #ffffff 90%, var(--ui-accent-2-soft)) 100%);
  box-shadow: var(--ui-shadow-xs);
  overflow: hidden;
  transition: border-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast),
    background-color var(--ui-transition-fast);
}

.day-group-card--active {
  border-color: color-mix(in srgb, var(--ui-accent-2) 50%, var(--ui-border));
  background: linear-gradient(150deg, #ffffff 0%, color-mix(in srgb, #ffffff 82%, var(--ui-accent-2-soft)) 100%);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ui-accent-2-soft) 72%, transparent), var(--ui-shadow-sm);
}

.day-group-card:focus-within {
  border-color: color-mix(in srgb, var(--ui-accent-2) 55%, var(--ui-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ui-focus-ring) 62%, transparent), var(--ui-shadow-sm);
}

.day-group-header {
  width: 100%;
  border: 0;
  background: color-mix(in srgb, #ffffff 80%, var(--ui-accent-2-soft));
  padding: 0.7rem 0.78rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
  cursor: pointer;
  transition: background-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast);
}

.day-group-header:hover {
  background: color-mix(in srgb, #ffffff 68%, var(--ui-accent-2-soft));
}

.day-group-header:focus-visible {
  outline: 2px solid var(--ui-focus-ring);
  outline-offset: -1px;
}

.day-group-header--expanded {
  background: color-mix(in srgb, #ffffff 62%, var(--ui-accent-2-soft));
  box-shadow: inset 0 -1px 0 color-mix(in srgb, var(--ui-accent-2) 28%, transparent);
}

.day-group-title {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--ui-accent-deep);
}

.day-group-icon {
  color: var(--ui-accent-2);
  font-size: 0.9rem;
}

.day-group-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.day-group-count {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 34%, var(--ui-border));
  padding: 0.16rem 0.5rem;
  background: #ffffff;
  color: color-mix(in srgb, var(--ui-accent-deep) 90%, var(--ui-text-primary));
  font-size: 0.74rem;
  font-weight: 600;
}

.day-group-chevron {
  color: var(--ui-accent-2);
  transition: transform var(--ui-transition-fast);
}

.day-group-chevron--expanded {
  transform: rotate(180deg);
}

.day-group-body {
  padding: 0.62rem 0.62rem 0.68rem;
}

.mobile-daily-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.mobile-daily-card {
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border));
  border-radius: 10px;
  padding: 0.62rem;
  background: linear-gradient(135deg, #ffffff 0%, var(--ui-accent-2-soft) 100%);
  box-shadow: var(--ui-shadow-xs);
  position: relative;
}

.mobile-daily-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.62rem;
  bottom: 0.62rem;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--ui-accent-2), var(--ui-coral));
}

.mobile-daily-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.4rem;
}

.mobile-daily-meta,
.mobile-daily-text {
  margin: 0.35rem 0 0;
  color: var(--ui-text-primary);
  font-size: 0.84rem;
}

.mobile-daily-text {
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  overflow: hidden;
  color: var(--ui-text-secondary);
  line-height: 1.4;
}

.mobile-daily-text--expanded {
  display: block;
  line-clamp: unset;
  -webkit-line-clamp: unset;
  overflow: visible;
}

.mobile-daily-actions {
  margin-top: 0.35rem;
  display: flex;
  justify-content: flex-end;
}

.weekly-row,
.daily-row {
  border: 1px solid var(--ui-border-soft);
  border-radius: 8px;
  padding: 0.5rem 0.55rem;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-xs);
  transition: border-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast),
    transform var(--ui-transition-fast);
}

.weekly-row {
  border: 1px solid var(--ui-border-soft);
  border-radius: 8px;
  padding: 0.5rem 0.55rem;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-xs);
  transition: border-color var(--ui-transition-fast), box-shadow var(--ui-transition-fast),
    transform var(--ui-transition-fast);
}
.weekly-row:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--ui-accent-2) 26%, var(--ui-border));
  box-shadow: var(--ui-shadow-sm);
}

.daily-row {
  border-color: color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border));
  border-radius: 10px;
  padding: 0.62rem 0.68rem 0.66rem;
  background: linear-gradient(135deg, #ffffff 0%, color-mix(in srgb, #ffffff 84%, var(--ui-accent-2-soft)) 100%);
  position: relative;
}

.daily-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.62rem;
  bottom: 0.62rem;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--ui-accent-2), var(--ui-coral));
}

.daily-row:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--ui-accent-2) 40%, var(--ui-border));
  box-shadow: var(--ui-shadow-sm);
}

.daily-row-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
}

.daily-row-head strong {
  color: color-mix(in srgb, var(--ui-accent-deep) 88%, var(--ui-accent-2));
}

.daily-row-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.daily-row-meta {
  margin: 0.35rem 0 0;
}

.daily-meta-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 40%, var(--ui-border));
  border-radius: 999px;
  padding: 0.18rem 0.52rem;
  background: color-mix(in srgb, #ffffff 78%, var(--ui-accent-2-soft));
  color: color-mix(in srgb, var(--ui-accent-deep) 90%, var(--ui-text-primary));
  font-size: 0.76rem;
  font-weight: 600;
}

.weekly-head {
  display: flex;
  justify-content: space-between;
  gap: 0.45rem;
  align-items: center;
}

.weekly-meta,
.weekly-feedback {
  margin: 0.25rem 0 0;
  color: var(--ui-text-secondary);
  font-size: 0.82rem;
}

.daily-text {
  margin: 0.3rem 0 0;
  color: var(--ui-text-primary);
  font-size: 0.86rem;
  line-height: 1.4;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.daily-text--expanded {
  display: block;
  line-clamp: unset;
  -webkit-line-clamp: unset;
  overflow: visible;
}

.daily-text-toggle {
  margin-top: 0.22rem;
  border: 0;
  background: transparent;
  color: var(--ui-accent-2);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.daily-text-toggle:hover {
  color: var(--ui-accent-deep);
  text-decoration: underline;
}

.daily-detail-body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.daily-detail-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.72rem 0.78rem;
  border: 1px solid var(--ui-border);
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, var(--ui-surface-tint) 58%, var(--ui-coral-soft) 100%);
  box-shadow: var(--ui-shadow-xs);
}

.daily-detail-identity {
  min-width: 0;
}

.daily-detail-name {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--ui-accent-deep);
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
}

.daily-detail-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
}

.daily-detail-meta {
  margin: 0.15rem 0 0;
  font-size: 0.86rem;
  color: var(--ui-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.daily-detail-date {
  margin: 0;
  font-size: 0.8rem;
  color: var(--ui-text-secondary);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.7rem;
}

.detail-block {
  border: 1px solid var(--ui-border);
  border-radius: 10px;
  padding: 0.7rem 0.72rem;
  background: #ffffff;
  box-shadow: var(--ui-shadow-xs);
}

.detail-block h4 {
  margin: 0;
  font-size: 0.84rem;
  color: var(--ui-text-primary);
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.detail-block p {
  margin: 0.35rem 0 0;
  color: var(--ui-text-primary);
  line-height: 1.45;
  white-space: pre-wrap;
}

.detail-block--done {
  background: var(--ui-success-soft);
  border-color: color-mix(in srgb, var(--ui-success) 36%, var(--ui-border));
}

.detail-block--done h4 {
  color: var(--ui-success);
}

.detail-block--plan {
  background: var(--ui-accent-2-soft);
  border-color: color-mix(in srgb, var(--ui-accent-2) 35%, var(--ui-border));
}

.detail-block--plan h4 {
  color: var(--ui-accent-2);
}

.detail-block--blockers {
  background: var(--ui-highlight-soft);
  border-color: color-mix(in srgb, var(--ui-highlight) 38%, var(--ui-border));
}

.detail-block--blockers h4 {
  color: var(--ui-warn);
}

.detail-block--resources {
  background: var(--ui-coral-soft);
  border-color: color-mix(in srgb, var(--ui-coral) 34%, var(--ui-border));
}

.detail-block--resources h4 {
  color: var(--ui-coral);
}

.detail-block--meta {
  background: var(--ui-surface-soft);
  border-color: var(--ui-border-soft);
}

.resource-list {
  list-style: none;
  margin: 0.4rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.resource-row {
  border: 1px solid var(--ui-border-soft);
  border-radius: 8px;
  padding: 0.45rem 0.5rem;
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  background: #ffffff;
}

.resource-main {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.resource-row a {
  color: var(--ui-accent-strong);
  text-decoration: none;
  font-size: 0.82rem;
  max-width: 55%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-row a:hover {
  text-decoration: underline;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.6rem;
}

:deep(.daily-detail-dialog .p-dialog-header) {
  background: #ffffff;
  border-bottom: 1px solid var(--ui-border);
}

:deep(.daily-detail-dialog .p-dialog-content) {
  background: var(--ui-surface-soft);
  padding-top: 0.9rem;
}

.centered {
  display: flex;
  justify-content: center;
  padding: 1.2rem;
}

.muted {
  color: var(--ui-text-secondary);
}

.small {
  margin: 0;
  font-size: 0.82rem;
}

@media (max-width: 1100px) {
  .reports-layout {
    grid-template-columns: 1fr;
  }

  .week-select {
    min-width: 0;
    width: 100%;
  }

  .card-head {
    flex-direction: column;
    align-items: stretch;
  }

  .daily-row-head,
  .daily-row-actions,
  .day-group-header,
  .daily-detail-hero,
  .resource-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-grid,
  .detail-meta-grid {
    grid-template-columns: 1fr;
  }

  .resource-row a {
    max-width: 100%;
  }
}
</style>
