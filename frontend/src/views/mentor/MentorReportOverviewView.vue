<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import { useMediaQuery } from '../../composables/useMediaQuery'
import type { AssignmentResponse, DailyReportResponse, TraineeResponse, WeeklySummaryResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

const query = ref('')
const trainees = ref<TraineeResponse[]>([])
const traineesLoading = ref(false)
const traineesError = ref('')
const selectedTraineeId = ref<number | null>(null)

const reportMode = ref<'weekly' | 'daily'>('weekly')
const dailyRangePreset = ref<'LAST_10_DAYS' | 'LAST_1_MONTH' | 'ALL'>('LAST_10_DAYS')
const dailyReports = ref<DailyReportResponse[]>([])
const dailyLoading = ref(false)
const dailyError = ref('')
const dailyDetailVisible = ref(false)
const dailyDetailItem = ref<DailyReportResponse | null>(null)

const detailLoading = ref(false)
const detailError = ref('')
const activeAssignment = ref<AssignmentResponse | null>(null)
const weeklySummaries = ref<WeeklySummaryResponse[]>([])
const selectedWeekStart = ref('')
const expandedWhatDoneIds = ref<number[]>([])

let detailRequestVersion = 0
let dailyRequestVersion = 0

const selectedTrainee = computed(() => trainees.value.find((item) => item.id === selectedTraineeId.value) ?? null)
const hasSelectedTrainee = computed(() => selectedTrainee.value != null)
const hasSearchQuery = computed(() => query.value.trim().length > 0)
const isMobileTable = useMediaQuery('(max-width: 900px)')
const sortedWeeklySummaries = computed(() =>
  [...weeklySummaries.value].sort((a, b) => b.weekStart.localeCompare(a.weekStart)),
)
const selectedWeeklySummary = computed(
  () => sortedWeeklySummaries.value.find((item) => item.weekStart === selectedWeekStart.value) ?? null,
)
const currentDailyRangeLabel = computed(() => {
  if (dailyRangePreset.value === 'LAST_10_DAYS') return 'last 10 days'
  if (dailyRangePreset.value === 'LAST_1_MONTH') return 'last 1 month'
  return 'all available days in active assignment'
})

function initials(name?: string): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return 'TR'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0] ?? ''}${parts[parts.length - 1][0] ?? ''}`.toUpperCase()
}

function formatDate(value?: string | null): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(d)
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

function calculateDailyRange(
  preset: 'LAST_10_DAYS' | 'LAST_1_MONTH' | 'ALL',
  assignmentAssignedAt?: string | null,
): { fromDate: string; toDate: string } {
  const today = new Date()
  const toDate = toIsoDate(today)

  if (preset === 'LAST_10_DAYS') {
    const from = new Date(today)
    from.setDate(from.getDate() - 9)
    return { fromDate: toIsoDate(from), toDate }
  }

  if (preset === 'LAST_1_MONTH') {
    const from = new Date(today)
    from.setMonth(from.getMonth() - 1)
    return { fromDate: toIsoDate(from), toDate }
  }

  const assignedAt = assignmentAssignedAt?.slice(0, 10)
  return { fromDate: assignedAt && assignedAt.length === 10 ? assignedAt : toDate, toDate }
}

async function loadTrainees(): Promise<void> {
  traineesLoading.value = true
  traineesError.value = ''
  try {
    const res = await mentorApi.listActiveTrainees({
      q: query.value.trim() || undefined,
      page: 0,
      size: 100,
      sortBy: 'fullName',
      sortDir: 'asc',
    })
    trainees.value = res.items

    if (trainees.value.length === 0) {
      selectedTraineeId.value = null
      return
    }

    const stillSelected = selectedTraineeId.value != null && trainees.value.some((item) => item.id === selectedTraineeId.value)
    if (!stillSelected) {
      selectedTraineeId.value = trainees.value[0].id
    }
  } catch (e) {
    traineesError.value = e instanceof ApiError ? e.message : 'Failed to load active trainees'
  } finally {
    traineesLoading.value = false
  }
}

async function loadSelectedTraineeReports(): Promise<void> {
  const trainee = selectedTrainee.value
  detailRequestVersion += 1
  const requestVersion = detailRequestVersion

  activeAssignment.value = null
  weeklySummaries.value = []
  selectedWeekStart.value = ''
  dailyReports.value = []
  dailyError.value = ''
  detailError.value = ''

  if (!trainee) {
    detailLoading.value = false
    return
  }

  detailLoading.value = true
  try {
    const assignment = await mentorApi.getTraineeActiveAssignmentOrNull(trainee.id)
    if (requestVersion !== detailRequestVersion) return
    activeAssignment.value = assignment
    if (!assignment) return

    const summaries = await mentorApi.listWeeklySummariesForTrainee(trainee.id, assignment.id)
    if (requestVersion !== detailRequestVersion) return
    weeklySummaries.value = summaries
  } catch (e) {
    if (requestVersion !== detailRequestVersion) return
    detailError.value = e instanceof ApiError ? e.message : 'Failed to load trainee reports'
  } finally {
    if (requestVersion === detailRequestVersion) {
      detailLoading.value = false
    }
  }
}

async function loadDailyReports(): Promise<void> {
  const trainee = selectedTrainee.value
  const assignment = activeAssignment.value
  dailyRequestVersion += 1
  const requestVersion = dailyRequestVersion

  dailyReports.value = []
  dailyError.value = ''

  if (!trainee || !assignment) {
    dailyLoading.value = false
    return
  }

  dailyLoading.value = true
  try {
    const range = calculateDailyRange(dailyRangePreset.value, assignment.assignedAt)
    const reports = await mentorApi.listDailyReportsForTraineeByRange(
      trainee.id,
      assignment.id,
      range.fromDate,
      range.toDate,
    )
    if (requestVersion !== dailyRequestVersion) return
    dailyReports.value = [...reports].sort((a, b) => b.reportDate.localeCompare(a.reportDate))
  } catch (e) {
    if (requestVersion !== dailyRequestVersion) return
    dailyError.value = e instanceof ApiError ? e.message : 'Failed to load daily reports'
  } finally {
    if (requestVersion === dailyRequestVersion) {
      dailyLoading.value = false
    }
  }
}

function selectTrainee(traineeId: number): void {
  selectedTraineeId.value = traineeId
}

async function searchTrainees(): Promise<void> {
  await loadTrainees()
}

async function clearSearch(): Promise<void> {
  query.value = ''
  await loadTrainees()
}

function openDailyDetail(item: DailyReportResponse): void {
  dailyDetailItem.value = item
  dailyDetailVisible.value = true
}

function closeDailyDetail(): void {
  dailyDetailVisible.value = false
  dailyDetailItem.value = null
}

function resolvedWhatDone(item: DailyReportResponse): string {
  return item.whatDone?.trim() || 'No update.'
}

function shouldShowWhatDoneToggle(item: DailyReportResponse): boolean {
  return resolvedWhatDone(item).length > 120
}

function isWhatDoneExpanded(reportId: number): boolean {
  return expandedWhatDoneIds.value.includes(reportId)
}

function toggleWhatDone(reportId: number): void {
  if (isWhatDoneExpanded(reportId)) {
    expandedWhatDoneIds.value = expandedWhatDoneIds.value.filter((id) => id !== reportId)
    return
  }
  expandedWhatDoneIds.value = [...expandedWhatDoneIds.value, reportId]
}

onMounted(async () => {
  await loadTrainees()
})

watch(selectedTraineeId, () => {
  void loadSelectedTraineeReports()
})

watch(sortedWeeklySummaries, (items) => {
  if (items.length === 0) {
    selectedWeekStart.value = ''
    return
  }
  const exists = items.some((item) => item.weekStart === selectedWeekStart.value)
  if (!exists) {
    selectedWeekStart.value = items[0].weekStart
  }
})

watch([reportMode, dailyRangePreset, () => activeAssignment.value?.id, selectedTraineeId], () => {
  if (reportMode.value === 'daily') {
    void loadDailyReports()
  }
})

watch(dailyReports, (items) => {
  const validIds = new Set(items.map((item) => item.id))
  expandedWhatDoneIds.value = expandedWhatDoneIds.value.filter((id) => validIds.has(id))
})
</script>

<template>
  <div class="mentor-reports-page">
    <PageHeader
      title="Mentor reports"
      description="Review active trainees and inspect their weekly reporting progress by current curriculum."
      tag-value="MVP: Active assignments only"
      tag-severity="warn"
    />

    <section class="reports-layout">
      <aside class="trainee-panel card-shell">
        <div class="panel-head">
          <h3>Active trainees</h3>
        </div>

        <div class="panel-tools">
          <IconField class="search-field">
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" placeholder="Search name or email" @keyup.enter="searchTrainees" />
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

        <Message v-if="traineesError" severity="error" :closable="false">{{ traineesError }}</Message>
        <div v-else-if="traineesLoading" class="centered">
          <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.1rem; height: 2.1rem" />
        </div>
        <p v-else-if="trainees.length === 0" class="muted small">No active trainees found.</p>
        <div v-else class="trainee-list">
          <button
            v-for="item in trainees"
            :key="item.id"
            type="button"
            class="trainee-item"
            :class="{ active: item.id === selectedTraineeId }"
            @click="selectTrainee(item.id)"
          >
            <div class="trainee-item-top">
              <div class="trainee-user">
                <Avatar :label="initials(item.fullName)" shape="circle" />
                <div class="trainee-main">
                  <p class="name">{{ item.fullName }}</p>
                  <p class="email">{{ item.email }}</p>
                </div>
              </div>
              <Tag value="ACTIVE" severity="success" rounded />
            </div>
            <p class="meta">
              {{ item.activeCurriculumName ?? 'No active assignment' }}
            </p>
            <p v-if="item.totalTaskCount != null" class="meta">
              {{ item.completedTaskCount ?? 0 }} / {{ item.totalTaskCount }} tasks completed
            </p>
          </button>
        </div>
      </aside>

      <section class="report-panel card-shell">
        <template v-if="!hasSelectedTrainee">
          <Message severity="info" :closable="false">Select an active trainee from the left panel to view reports.</Message>
        </template>

        <template v-else>
          <div class="report-head">
            <div class="report-identity">
              <Avatar :label="initials(selectedTrainee!.fullName)" shape="circle" size="large" />
              <div class="report-head-main">
                <h3>{{ selectedTrainee!.fullName }}</h3>
                <p class="report-email">{{ selectedTrainee!.email }}</p>
              </div>
            </div>
            <p class="active-only-note">MVP currently shows ACTIVE assignment only</p>
          </div>

          <div v-if="detailLoading" class="centered">
            <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.25rem; height: 2.25rem" />
          </div>

          <Message v-else-if="detailError" severity="error" :closable="false">{{ detailError }}</Message>

          <template v-else-if="!activeAssignment">
            <Message severity="info" :closable="false">
              This trainee has no active assignment, so there are no active reports to show.
            </Message>
          </template>

          <section v-else class="assignment-section">
            <header class="assignment-head">
              <div>
                <p class="assignment-title">{{ activeAssignment!.curriculumName }}</p>
                <p class="assignment-meta">
                  Assigned {{ formatDate(activeAssignment!.assignedAt) }} · Status {{ activeAssignment!.status }}
                </p>
              </div>
              <Tag value="ACTIVE" severity="info" rounded />
            </header>

            <div class="report-mode-tabs">
              <Button
                label="Weekly report"
                size="small"
                :severity="reportMode === 'weekly' ? undefined : 'secondary'"
                :outlined="reportMode !== 'weekly'"
                @click="reportMode = 'weekly'"
              />
              <Button
                label="Daily report"
                size="small"
                :severity="reportMode === 'daily' ? undefined : 'secondary'"
                :outlined="reportMode !== 'daily'"
                @click="reportMode = 'daily'"
              />
            </div>

            <article v-if="reportMode === 'weekly'" class="surface-card">
              <div class="card-head">
                <h4>Weekly reports</h4>
                <select v-model="selectedWeekStart" class="week-select" :disabled="sortedWeeklySummaries.length === 0">
                  <option v-for="item in sortedWeeklySummaries" :key="item.id" :value="item.weekStart">
                    {{ item.weekStart }} - {{ item.weekEnd }}
                  </option>
                </select>
              </div>

              <p v-if="sortedWeeklySummaries.length === 0" class="muted small">
                No weekly summary yet. Latest week will appear here once a summary is generated/reviewed.
              </p>

              <div v-else-if="selectedWeeklySummary" class="weekly-row">
                <div class="weekly-head">
                  <strong>{{ selectedWeeklySummary.weekStart }} - {{ selectedWeeklySummary.weekEnd }}</strong>
                  <Tag
                    :value="selectedWeeklySummary.reviewStatus"
                    :severity="selectedWeeklySummary.reviewStatus === 'REVIEWED' ? 'success' : 'secondary'"
                    rounded
                  />
                </div>
                <p class="weekly-meta">
                  Grade: {{ selectedWeeklySummary.mentorGrade != null ? `${selectedWeeklySummary.mentorGrade}/10` : 'Not graded' }} ·
                  {{ selectedWeeklySummary.finalizedAt ? 'Finalized' : 'Open' }}
                </p>
                <p class="weekly-feedback">{{ selectedWeeklySummary.mentorFeedback || 'No mentor feedback yet.' }}</p>
                <p class="weekly-feedback">
                  {{ selectedWeeklySummary.summaryText || 'No generated weekly summary text yet.' }}
                </p>
              </div>
            </article>

            <article v-else class="surface-card">
              <div class="card-head">
                <h4>Daily reports</h4>
                <div class="daily-presets">
                  <Button
                    label="10 days"
                    size="small"
                    text
                    :class="{ 'is-active': dailyRangePreset === 'LAST_10_DAYS' }"
                    @click="dailyRangePreset = 'LAST_10_DAYS'"
                  />
                  <Button
                    label="1 month"
                    size="small"
                    text
                    :class="{ 'is-active': dailyRangePreset === 'LAST_1_MONTH' }"
                    @click="dailyRangePreset = 'LAST_1_MONTH'"
                  />
                  <Button
                    label="All"
                    size="small"
                    text
                    :class="{ 'is-active': dailyRangePreset === 'ALL' }"
                    @click="dailyRangePreset = 'ALL'"
                  />
                </div>
              </div>

              <p class="muted small">Showing {{ currentDailyRangeLabel }}.</p>
              <p v-if="dailyLoading" class="muted small">Loading daily reports...</p>
              <Message v-else-if="dailyError" severity="error" :closable="false">{{ dailyError }}</Message>
              <p v-else-if="dailyReports.length === 0" class="muted small">No daily reports found for this range.</p>
              <ul v-else-if="!isMobileTable" class="daily-list">
                <li v-for="item in dailyReports" :key="item.id" class="daily-row">
                  <div class="daily-row-head">
                    <strong>{{ item.reportDate }}</strong>
                    <div class="daily-row-actions">
                      <Tag :value="item.status" :severity="item.status === 'SUBMITTED' ? 'info' : 'secondary'" rounded />
                      <Button label="View details" size="small" text @click="openDailyDetail(item)" />
                    </div>
                  </div>
                  <p class="daily-row-meta">
                    <span class="daily-meta-chip">{{ item.fresherLabel }} · Day {{ item.trainingDayIndex }}</span>
                  </p>
                  <p class="daily-text" :class="{ 'daily-text--expanded': isWhatDoneExpanded(item.id) }">
                    {{ resolvedWhatDone(item) }}
                  </p>
                  <button
                    v-if="shouldShowWhatDoneToggle(item)"
                    type="button"
                    class="daily-text-toggle"
                    @click="toggleWhatDone(item.id)"
                  >
                    {{ isWhatDoneExpanded(item.id) ? 'See less' : 'See more' }}
                  </button>
                </li>
              </ul>
              <ul v-else class="mobile-daily-list">
                <li v-for="item in dailyReports" :key="item.id" class="mobile-daily-card">
                  <div class="mobile-daily-head">
                    <strong>{{ item.reportDate }}</strong>
                    <Tag :value="item.status" :severity="item.status === 'SUBMITTED' ? 'info' : 'secondary'" rounded />
                  </div>
                  <p class="mobile-daily-meta">
                    <span class="daily-meta-chip">{{ item.fresherLabel }} · Training day {{ item.trainingDayIndex }}</span>
                  </p>
                  <p class="mobile-daily-text" :class="{ 'mobile-daily-text--expanded': isWhatDoneExpanded(item.id) }">
                    {{ resolvedWhatDone(item) }}
                  </p>
                  <button
                    v-if="shouldShowWhatDoneToggle(item)"
                    type="button"
                    class="daily-text-toggle"
                    @click="toggleWhatDone(item.id)"
                  >
                    {{ isWhatDoneExpanded(item.id) ? 'See less' : 'See more' }}
                  </button>
                  <div class="mobile-daily-actions">
                    <Button label="View details" size="small" text @click="openDailyDetail(item)" />
                  </div>
                </li>
              </ul>
            </article>
          </section>
        </template>
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
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.card-shell {
  background: var(--ui-surface);
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-lg);
  box-shadow: var(--ui-shadow-md);
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
}

.report-head {
  display: flex;
  justify-content: space-between;
  gap: 0.65rem;
  align-items: center;
  flex-wrap: wrap;
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
  color: var(--ui-text-secondary);
  font-weight: 500;
}

.active-only-note {
  margin: 0;
  font-size: 0.78rem;
  color: var(--ui-warn);
  padding: 0.25rem 0.5rem;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--ui-highlight) 45%, transparent);
  background: var(--ui-highlight-soft);
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
  border: 1px solid var(--ui-border);
  border-radius: 10px;
  background: var(--ui-surface);
  padding: 0.75rem;
  box-shadow: var(--ui-shadow-xs);
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
  flex-wrap: wrap;
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
    width: 100%;
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
  background: linear-gradient(135deg, #ffffff 0%, var(--ui-accent-2-soft) 100%);
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
  color: var(--ui-accent-deep);
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
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 34%, var(--ui-border));
  border-radius: 999px;
  padding: 0.16rem 0.5rem;
  background: #ffffff;
  color: var(--ui-accent-deep);
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
