<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import PageHeader from '../../components/layout/PageHeader.vue'
import { useTraineeDailyReport } from '../../composables/useTraineeDailyReport'
import { injectTraineeAssignment, taskStatusLabel, taskStatusTagSeverity } from '../../composables/useTraineeAssignment'
import { useAuthStore } from '../../stores/auth'

const { assignment, tasks: assignmentTasks, hasAssignment, loading: assignmentLoading, error: assignmentError } = injectTraineeAssignment()
const auth = useAuthStore()

const traineeDisplayName = computed(() => {
  const email = auth.user?.email ?? ''
  const local = email.split('@')[0] ?? ''
  const first = local.split(/[._-]/)[0] ?? local
  if (!first) return 'Fresher'
  return first.charAt(0).toUpperCase() + first.slice(1)
})

const assignmentId = computed(() => assignment.value?.id ?? null)

const {
  loading,
  submitting,
  error,
  weekStart,
  selectedDate,
  weekReports,
  weeklySummaries,
  reportStatus,
  canEdit,
  fresherLabel,
  trainingDayIndex,
  whatDone,
  plannedTomorrow,
  blockers,
  resources,
  loadWeek,
  saveDraft,
  submit,
} = useTraineeDailyReport(assignmentId, traineeDisplayName)

const mode = ref<'list' | 'create' | 'edit'>('list')
const editingReportDate = ref<string | null>(null)

function toIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseIsoDate(value: string): Date {
  return new Date(`${value}T00:00:00`)
}

const weekStartModel = computed(() => toIsoDate(weekStart.value))
const selectedDateModel = computed(() => toIsoDate(selectedDate.value))
const latestWeeklySummary = computed(() => weeklySummaries.value[0] ?? null)
const todayIso = computed(() => toIsoDate(new Date()))

const todayReports = computed(() => weekReports.value.filter((item) => item.reportDate === todayIso.value))
const previousReports = computed(() => weekReports.value.filter((item) => item.reportDate < todayIso.value).reverse())
const orderedAssignmentTasks = computed(() => [...assignmentTasks.value].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id))
const focusTask = computed(() => {
  const inProgress = orderedAssignmentTasks.value.find((task) => task.status === 'IN_PROGRESS')
  if (inProgress) return inProgress
  return orderedAssignmentTasks.value.find((task) => task.status !== 'DONE') ?? orderedAssignmentTasks.value[0] ?? null
})

interface DeadlineAlert {
  tone: 'secondary' | 'info' | 'warn' | 'danger'
  title: string
  detail: string
}

const deadlineAlert = computed<DeadlineAlert>(() => {
  const raw = assignment.value?.endedAt?.trim()
  if (!raw) {
    return {
      tone: 'secondary',
      title: 'No final deadline set',
      detail: 'Mentor has not set assignment end date yet.',
    }
  }

  const deadlineDatePart = raw.slice(0, 10)
  const [year, month, day] = deadlineDatePart.split('-').map((part) => Number(part))
  if (!year || !month || !day) {
    return {
      tone: 'secondary',
      title: 'Deadline unavailable',
      detail: 'Could not parse assignment deadline from server.',
    }
  }

  const deadlineEndOfDay = new Date(year, month - 1, day, 23, 59, 59, 999)
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const daysLeft = Math.floor((deadlineEndOfDay.getTime() - todayStart.getTime()) / (24 * 60 * 60 * 1000))
  const deadlineLabel = `Assignment end: ${deadlineDatePart}`

  if (daysLeft < 0) {
    const overdueDays = Math.abs(daysLeft)
    return {
      tone: 'danger',
      title: `Overdue ${overdueDays} day(s)`,
      detail: deadlineLabel,
    }
  }
  if (daysLeft === 0) {
    return {
      tone: 'warn',
      title: 'Due today',
      detail: deadlineLabel,
    }
  }
  if (daysLeft <= 7) {
    return {
      tone: 'warn',
      title: `${daysLeft} day(s) left`,
      detail: deadlineLabel,
    }
  }
  return {
    tone: 'info',
    title: `${daysLeft} day(s) left`,
    detail: deadlineLabel,
  }
})

const deadlineMessageSeverity = computed<'info' | 'warn' | 'error' | 'secondary'>(() => {
  if (deadlineAlert.value.tone === 'danger') return 'error'
  return deadlineAlert.value.tone
})

function updateWeekStart(value: string): void {
  weekStart.value = parseIsoDate(value)
  void loadWeek()
}

function pickReportDate(date: string): void {
  selectedDate.value = parseIsoDate(date)
  editingReportDate.value = date
  mode.value = 'edit'
}

function createTodayReport(): void {
  const todayReport = weekReports.value.find((item) => item.reportDate === todayIso.value)
  selectedDate.value = parseIsoDate(todayIso.value)
  editingReportDate.value = todayIso.value
  mode.value = todayReport ? 'edit' : 'create'
}

function setResourceField(index: number, field: 'type' | 'label' | 'url', value: string): void {
  const next = [...resources.value]
  const current = next[index]
  if (!current) return
  next[index] = {
    ...current,
    [field]: value,
  }
  resources.value = next
}

function addResourceRow(): void {
  resources.value = [...resources.value, { type: '', label: '', url: '' }]
}

function removeResourceRow(index: number): void {
  resources.value = resources.value.filter((_, i) => i !== index)
  if (resources.value.length === 0) {
    resources.value = [{ type: '', label: '', url: '' }]
  }
}

function openCreateReport(): void {
  createTodayReport()
}

function cancelEdit(): void {
  mode.value = 'list'
  editingReportDate.value = null
}

async function saveDraftAndBack(): Promise<void> {
  await saveDraft()
  mode.value = 'list'
}

async function submitAndBack(): Promise<void> {
  await submit()
  mode.value = 'list'
}

const formTitle = computed(() => {
  if (mode.value === 'create') return `Create report for ${selectedDateModel.value}`
  return `Edit report ${editingReportDate.value ?? selectedDateModel.value}`
})

const pageTag = computed(() => {
  if (mode.value === 'list') return `${weekReports.value.length} report(s)`
  return reportStatus.value ? `Current: ${reportStatus.value}` : undefined
})

const isDialogVisible = computed({
  get: () => mode.value !== 'list',
  set: (visible: boolean) => {
    if (!visible) cancelEdit()
  },
})

watch(
  assignmentId,
  () => {
    mode.value = 'list'
    editingReportDate.value = null
    void loadWeek()
  },
  { immediate: true },
)
</script>

<template>
  <div class="daily-page">
    <PageHeader
      title="Daily report"
      description="Capture today progress, keep previous reports, and submit updates for mentor review."
      :tag-value="pageTag"
      tag-severity="info"
    />

    <Message v-if="assignmentError" severity="error" :closable="false">{{ assignmentError }}</Message>

    <div v-if="assignmentLoading" class="centered">
      <ProgressSpinner stroke-width="3" animation-duration=".8s" />
    </div>

    <template v-else-if="!hasAssignment">
      <Message severity="info" :closable="false">
        No active assignment. Ask your mentor to assign a published curriculum before sending daily reports.
      </Message>
    </template>

    <div v-else class="daily-layout">
      <section class="daily-summary card-shell">
        <div class="history-head">
          <h3>Daily summary</h3>
          <Button label="Create report" icon="pi pi-plus" size="small" @click="openCreateReport" />
        </div>
        <label class="field">
          Week start
          <input type="date" :value="weekStartModel" @change="updateWeekStart(($event.target as HTMLInputElement).value)" />
        </label>
        <Button
          label="Reload"
          icon="pi pi-refresh"
          text
          size="small"
          :loading="loading"
          @click="loadWeek"
        />
        <section class="focus-card">
          <div class="focus-card-head">
            <h4>Current focus</h4>
            <Tag
              v-if="focusTask"
              :value="taskStatusLabel(focusTask.status)"
              :severity="taskStatusTagSeverity(focusTask.status)"
              rounded
            />
          </div>
          <p v-if="focusTask" class="focus-title">Step {{ focusTask.sortOrder }} · {{ focusTask.title }}</p>
          <p v-else class="muted small">No task found in current assignment.</p>
          <Message :severity="deadlineMessageSeverity" :closable="false" class="deadline-alert">
            <strong>{{ deadlineAlert.title }}</strong>
            <span>{{ deadlineAlert.detail }}</span>
          </Message>
        </section>
      </section>

      <aside class="daily-history card-shell">
        <div class="history-head">
          <h3>Reports timeline</h3>
        </div>
        <section class="history-group">
          <h4>Today</h4>
          <p v-if="todayReports.length === 0" class="muted small">No report for today yet.</p>
          <button
            v-for="item in todayReports"
            :key="item.id"
            class="report-item"
            :class="{ active: item.reportDate === selectedDateModel }"
            type="button"
            @click="pickReportDate(item.reportDate)"
          >
            <div class="report-item-top">
              <strong>{{ item.reportDate }}</strong>
              <Tag :value="item.status" :severity="item.status === 'SUBMITTED' ? 'info' : 'secondary'" rounded />
            </div>
            <p>{{ item.whatDone || 'No details yet' }}</p>
          </button>
        </section>

        <section class="history-group">
          <h4>Previous days</h4>
          <p v-if="previousReports.length === 0" class="muted small">No previous reports in selected week.</p>
          <button
            v-for="item in previousReports"
            :key="item.id"
            class="report-item"
            :class="{ active: item.reportDate === selectedDateModel }"
            type="button"
            @click="pickReportDate(item.reportDate)"
          >
            <div class="report-item-top">
              <strong>{{ item.reportDate }}</strong>
              <Tag :value="item.status" :severity="item.status === 'SUBMITTED' ? 'info' : 'secondary'" rounded />
            </div>
            <p>{{ item.whatDone || 'No details yet' }}</p>
          </button>
        </section>
      </aside>
    </div>

    <Dialog
      v-model:visible="isDialogVisible"
      modal
      dismissable-mask
      close-on-escape
      :draggable="false"
      :closable="false"
      :style="{ width: 'min(960px, 92vw)' }"
      class="daily-report-dialog"
    >
      <section class="daily-form">
        <header class="form-head dialog-head">
          <div>
            <h3>{{ formTitle }}</h3>
            <p class="muted">Daily Report &lt; {{ selectedDateModel }} &gt;</p>
          </div>
          <div class="dialog-head-actions">
            <Tag :value="reportStatus ? reportStatus : 'NOT_SUBMITTED'" :severity="reportStatus ? 'info' : 'secondary'" rounded />
            <Button icon="pi pi-times" text rounded severity="secondary" :disabled="submitting" @click="cancelEdit" />
          </div>
        </header>

        <div class="daily-form-body">
          <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
          <Message v-else-if="!canEdit" severity="warn" :closable="false">
            This week is locked. Daily report can no longer be edited.
          </Message>

          <div class="form-grid">
            <label>
              Fresher label
              <InputText v-model="fresherLabel" :disabled="!canEdit || submitting" />
            </label>
            <label>
              Training day index
              <InputNumber
                :model-value="trainingDayIndex"
                :min="1"
                :disabled="!canEdit || submitting"
                @update:model-value="trainingDayIndex = $event as number | null"
              />
            </label>
            <label class="full">
              Noi dung cong viec hom nay
              <Textarea v-model="whatDone" rows="3" auto-resize :disabled="!canEdit || submitting" />
            </label>
            <label class="full">
              Noi dung cong viec ngay mai
              <Textarea v-model="plannedTomorrow" rows="3" auto-resize :disabled="!canEdit || submitting" />
            </label>
            <label class="full">
              Kho khan
              <Textarea v-model="blockers" rows="2" auto-resize :disabled="!canEdit || submitting" />
            </label>

            <div class="full report-resources">
              <div class="report-resources-head">
                <p>Proof resources</p>
                <Button
                  label="Add resource"
                  icon="pi pi-plus"
                  text
                  size="small"
                  :disabled="!canEdit || submitting"
                  @click="addResourceRow"
                />
              </div>
              <div class="report-resources-list">
                <div v-for="(item, index) in resources" :key="`resource-${index}`" class="report-resource-row">
                  <InputText
                    :model-value="item.type"
                    placeholder="Type (e.g. TRELLO)"
                    :disabled="!canEdit || submitting"
                    @update:model-value="setResourceField(index, 'type', String($event ?? ''))"
                  />
                  <InputText
                    :model-value="item.label ?? ''"
                    placeholder="Label"
                    :disabled="!canEdit || submitting"
                    @update:model-value="setResourceField(index, 'label', String($event ?? ''))"
                  />
                  <InputText
                    :model-value="item.url"
                    placeholder="URL"
                    :disabled="!canEdit || submitting"
                    @update:model-value="setResourceField(index, 'url', String($event ?? ''))"
                  />
                  <Button
                    icon="pi pi-trash"
                    text
                    severity="danger"
                    :disabled="!canEdit || submitting"
                    @click="removeResourceRow(index)"
                  />
                </div>
              </div>
            </div>
          </div>

          <div v-if="latestWeeklySummary" class="weekly-feedback">
            <p class="weekly-feedback-title">Latest weekly feedback</p>
            <p class="weekly-feedback-meta">
              Week {{ latestWeeklySummary.weekStart }} - {{ latestWeeklySummary.weekEnd }} ·
              {{ latestWeeklySummary.reviewStatus }}
            </p>
            <p v-if="latestWeeklySummary.mentorGrade != null" class="weekly-feedback-grade">
              Mentor grade: {{ latestWeeklySummary.mentorGrade }}/10
            </p>
            <p class="weekly-feedback-text">
              {{ latestWeeklySummary.mentorFeedback || 'No mentor feedback yet.' }}
            </p>
          </div>
        </div>

        <footer class="form-actions daily-form-footer">
          <Button
            label="Save draft"
            severity="secondary"
            outlined
            :disabled="!canEdit || submitting"
            :loading="submitting"
            @click="saveDraftAndBack"
          />
          <Button
            label="Submit report"
            :disabled="!canEdit || submitting"
            :loading="submitting"
            @click="submitAndBack"
          />
          <Button
            label="Cancel"
            text
            severity="secondary"
            :disabled="submitting"
            @click="cancelEdit"
          />
        </footer>
      </section>
    </Dialog>
  </div>
</template>

<style scoped>
.daily-page {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.centered {
  display: flex;
  justify-content: center;
  padding: 2rem;
}

.daily-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.card-shell {
  border: 1px solid #ddd6fe;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  padding: 0.9rem;
}

.daily-summary,
.daily-history {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.history-head h3,
.daily-form h3 {
  margin: 0;
}

.focus-card {
  border: 1px solid #ddd6fe;
  border-radius: 12px;
  background: #faf8ff;
  padding: 0.7rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.focus-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.focus-card-head h4 {
  margin: 0;
  font-size: 0.88rem;
}

.focus-title {
  margin: 0;
  font-size: 0.9rem;
  color: #1e293b;
  font-weight: 600;
}

.deadline-alert {
  margin-top: 0.15rem;
}

.deadline-alert :deep(.p-message-text) {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
}

.field input[type='date'] {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0.35rem 0.45rem;
}

.history-group h4 {
  margin: 0 0 0.35rem;
  font-size: 0.88rem;
}

.report-item {
  width: 100%;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 0.5rem 0.55rem;
  margin-bottom: 0.45rem;
  cursor: pointer;
}

.report-item.active {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.12);
}

.report-item-top {
  display: flex;
  justify-content: space-between;
  gap: 0.45rem;
  align-items: center;
}

.report-item p {
  margin: 0.35rem 0 0;
  color: #64748b;
  font-size: 0.82rem;
}

.daily-form {
  display: flex;
  flex-direction: column;
}

.daily-form-placeholder h3 {
  margin: 0;
  font-size: 1rem;
}

.daily-form-placeholder p {
  margin: 0.35rem 0 0;
}

.form-head {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: flex-start;
}

.dialog-head {
  border-bottom: 1px solid #ede9fe;
  padding: 1rem 1rem 0.85rem;
  flex-shrink: 0;
}

.dialog-head-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.daily-form-body {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0.95rem 1rem;
  overflow-y: auto;
  max-height: min(60vh, 620px);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.7rem;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.86rem;
}

.form-grid .full {
  grid-column: 1 / -1;
}

.report-resources {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 0.55rem 0.6rem;
  background: #f8fafc;
}

.report-resources-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.report-resources-head p {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 600;
}

.report-resources-list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.report-resource-row {
  display: grid;
  grid-template-columns: 150px 1fr 2fr auto;
  gap: 0.45rem;
  align-items: center;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.daily-form-footer {
  border-top: 1px solid #ede9fe;
  padding: 0.8rem 1rem 1rem;
  margin-top: auto;
  background: #ffffff;
  flex-shrink: 0;
}

:deep(.daily-report-dialog.p-dialog) {
  border: 1px solid #d4ccff;
  border-radius: 20px;
  box-shadow: 0 24px 56px -26px rgba(15, 23, 42, 0.48);
  overflow: hidden;
}

:deep(.daily-report-dialog .p-dialog-content) {
  padding: 0;
}

.weekly-feedback {
  border-top: 1px solid #e2e8f0;
  padding-top: 0.65rem;
}

.weekly-feedback-title {
  margin: 0;
  font-weight: 600;
}

.weekly-feedback-meta,
.weekly-feedback-grade,
.weekly-feedback-text {
  margin: 0.25rem 0 0;
  font-size: 0.85rem;
  color: #475569;
}

.weekly-feedback-grade {
  font-weight: 600;
  color: #1f2937;
}

.muted {
  color: #64748b;
}

.small {
  margin: 0;
  font-size: 0.82rem;
}

@media (max-width: 1100px) {
  .daily-layout {
    grid-template-columns: 1fr;
  }

  .daily-form-body {
    max-height: min(62vh, 520px);
  }

  .form-grid,
  .report-resource-row {
    grid-template-columns: 1fr;
  }

  .daily-form-footer {
    justify-content: stretch;
  }

  .daily-form-footer :deep(.p-button) {
    width: 100%;
  }
}
</style>
