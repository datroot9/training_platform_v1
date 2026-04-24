<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import * as traineeApi from '../../api/modules/trainee'
import { ApiError } from '../../api/client'
import type { AssignmentResponse, WeeklySummaryResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

const assignments = ref<AssignmentResponse[]>([])
const assignmentsLoading = ref(true)
const assignmentsError = ref('')
const selectedAssignmentId = ref<number | null>(null)

const summaries = ref<WeeklySummaryResponse[]>([])
const summariesLoading = ref(false)
const summariesError = ref('')

const assignmentOptions = computed(() =>
  assignments.value.map((item) => ({
    id: item.id,
    label: item.status === 'ACTIVE' ? `${item.curriculumName} (active)` : `${item.curriculumName} (${item.status})`,
  })),
)

const sortedSummaries = computed(() =>
  [...summaries.value].sort((a, b) => {
    const byWeek = b.weekStart.localeCompare(a.weekStart)
    if (byWeek !== 0) return byWeek
    return (b.generatedAt ?? '').localeCompare(a.generatedAt ?? '')
  }),
)

function normalizedLines(items: string[] | null | undefined): string[] {
  return (items ?? []).map((line) => line.trim()).filter((line) => line.length > 0)
}

async function loadAssignments(): Promise<void> {
  assignmentsLoading.value = true
  assignmentsError.value = ''
  try {
    const list = await traineeApi.listAssignments()
    assignments.value = list
    const activeId = list.find((item) => item.status === 'ACTIVE')?.id
    const currentStillValid =
      selectedAssignmentId.value != null && list.some((item) => item.id === selectedAssignmentId.value)
    if (!currentStillValid) {
      selectedAssignmentId.value = activeId ?? list[0]?.id ?? null
    }
  } catch (e) {
    assignmentsError.value = e instanceof ApiError ? e.message : 'Could not load assignments'
    assignments.value = []
    selectedAssignmentId.value = null
  } finally {
    assignmentsLoading.value = false
  }
}

async function loadWeeklyFeedback(): Promise<void> {
  const assignmentId = selectedAssignmentId.value
  if (assignmentId == null) {
    summaries.value = []
    summariesError.value = ''
    return
  }
  summariesLoading.value = true
  summariesError.value = ''
  try {
    summaries.value = await traineeApi.getWeeklySummaries(assignmentId)
  } catch (e) {
    summariesError.value = e instanceof ApiError ? e.message : 'Could not load weekly feedback'
  } finally {
    summariesLoading.value = false
  }
}

watch(selectedAssignmentId, () => {
  void loadWeeklyFeedback()
})

void loadAssignments().then(() => loadWeeklyFeedback())
</script>

<template>
  <div class="weekly-page">
    <PageHeader
      title="Weekly feedback"
      description="Review mentor feedback history, weekly grades, and finalized status for your assignment."
    />

    <section class="card-shell filters">
      <label class="field">
        <span>Assignment</span>
        <Select
          :model-value="selectedAssignmentId"
          :options="assignmentOptions"
          option-label="label"
          option-value="id"
          :disabled="assignmentsLoading || assignmentOptions.length === 0"
          placeholder="Select assignment"
          @update:model-value="selectedAssignmentId = ($event as number | null) ?? null"
        />
      </label>
    </section>

    <section class="card-shell content">
      <Message v-if="assignmentsError" severity="error" :closable="false">{{ assignmentsError }}</Message>
      <div v-else-if="assignmentsLoading" class="centered">
        <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.75rem; height: 2.75rem" />
      </div>
      <Message v-else-if="assignmentOptions.length === 0" severity="info" :closable="false">
        No assignment available yet. Once your mentor assigns a curriculum, weekly feedback will appear here.
      </Message>

      <template v-else>
        <Message v-if="summariesError" severity="error" :closable="false">{{ summariesError }}</Message>
        <div v-else-if="summariesLoading" class="centered">
          <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.5rem; height: 2.5rem" />
        </div>
        <p v-else-if="sortedSummaries.length === 0" class="muted">
          No weekly feedback yet for this assignment.
        </p>

        <ul v-else class="summary-list">
          <li v-for="item in sortedSummaries" :key="item.id" class="summary-card">
            <div class="summary-head">
              <strong class="summary-week"><i class="pi pi-calendar" /> {{ item.weekStart }} - {{ item.weekEnd }}</strong>
              <Tag :value="item.reviewStatus" :severity="item.reviewStatus === 'REVIEWED' ? 'success' : 'secondary'" rounded />
            </div>
            <p class="summary-meta">
              <span class="chip chip--grade"><i class="pi pi-star-fill" /> Grade: {{ item.mentorGrade != null ? `${item.mentorGrade}/10` : 'Not graded' }}</span>
              <span class="chip chip--lock">
                <i :class="item.finalizedAt ? 'pi pi-lock' : 'pi pi-lock-open'" />
                {{ item.finalizedAt ? 'Finalized' : 'Open' }}
              </span>
            </p>

            <div class="sections">
              <div class="section-card section-card--done">
                <p class="section-title"><i class="pi pi-check-circle" /> What was accomplished</p>
                <ul v-if="normalizedLines(item.accomplishments).length > 0" class="line-list">
                  <li v-for="(line, idx) in normalizedLines(item.accomplishments)" :key="`done-${item.id}-${idx}`">{{ line }}</li>
                </ul>
                <p v-else class="fallback"><i class="pi pi-info-circle" /> No work logged for this week.</p>
              </div>

              <div class="section-card section-card--risk">
                <p class="section-title"><i class="pi pi-exclamation-triangle" /> Difficulties / blockers</p>
                <ul v-if="normalizedLines(item.difficulties).length > 0" class="line-list">
                  <li v-for="(line, idx) in normalizedLines(item.difficulties)" :key="`diff-${item.id}-${idx}`">{{ line }}</li>
                </ul>
                <p v-else class="fallback"><i class="pi pi-info-circle" /> No difficulties noted for this week.</p>
              </div>
            </div>

            <div class="feedback-box">
              <p class="feedback-label"><i class="pi pi-comment" /> Mentor feedback</p>
              <p class="feedback-text">{{ item.mentorFeedback || 'No mentor feedback yet.' }}</p>
            </div>
          </li>
        </ul>
      </template>
    </section>
  </div>
</template>

<style scoped>
.weekly-page {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.card-shell {
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 34%, var(--ui-border));
  border-radius: 12px;
  background: linear-gradient(
    155deg,
    color-mix(in srgb, #ffffff 74%, var(--ui-accent-soft-2)) 0%,
    color-mix(in srgb, #ffffff 66%, var(--ui-accent-soft)) 58%,
    color-mix(in srgb, #ffffff 72%, var(--ui-accent-2-soft)) 100%
  );
  padding: 0.85rem 0.9rem;
  box-shadow:
    0 10px 22px -18px color-mix(in srgb, var(--ui-accent-2) 48%, transparent),
    var(--ui-shadow-xs);
}

.filters .field {
  display: grid;
  gap: 0.35rem;
  max-width: 24rem;
}

.filters .field span {
  font-size: 0.82rem;
  font-weight: 700;
  color: color-mix(in srgb, var(--ui-accent-deep) 85%, var(--ui-accent-2));
}

.centered {
  display: flex;
  justify-content: center;
  padding: 1rem 0;
}

.muted {
  margin: 0;
  color: var(--ui-text-secondary);
}

.summary-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.62rem;
}

.summary-card {
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 30%, var(--ui-border-soft));
  border-radius: 12px;
  padding: 0.78rem 0.86rem;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, #ffffff 72%, var(--ui-accent-soft-2)) 0%,
    color-mix(in srgb, #ffffff 64%, var(--ui-accent-2-soft)) 100%
  );
  box-shadow:
    0 10px 20px -16px color-mix(in srgb, var(--ui-accent-2) 44%, transparent),
    var(--ui-shadow-xs);
}

.summary-head {
  display: flex;
  justify-content: space-between;
  gap: 0.4rem;
  align-items: center;
}

.summary-head strong {
  color: color-mix(in srgb, var(--ui-accent-deep) 86%, var(--ui-accent-2));
  font-size: 0.9rem;
  font-weight: 700;
}

.summary-week {
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
}

.summary-week i {
  color: var(--ui-accent-2);
}

.summary-meta {
  margin: 0.35rem 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border-radius: 999px;
  padding: 0.18rem 0.52rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: color-mix(in srgb, var(--ui-accent-deep) 85%, var(--ui-accent-2));
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 34%, var(--ui-border-soft));
  background: color-mix(in srgb, #ffffff 68%, var(--ui-accent-2-soft));
}

.chip i {
  font-size: 0.72rem;
}

.chip--grade {
  border-color: color-mix(in srgb, var(--ui-success) 36%, var(--ui-border-soft));
  background: color-mix(in srgb, #ffffff 64%, var(--ui-success-soft));
  color: color-mix(in srgb, var(--ui-success) 88%, var(--ui-text-primary));
}

.chip--lock {
  border-color: color-mix(in srgb, var(--ui-highlight) 34%, var(--ui-border-soft));
  background: color-mix(in srgb, #ffffff 64%, var(--ui-highlight-soft));
  color: color-mix(in srgb, var(--ui-heading) 84%, var(--ui-accent-deep));
}

.sections {
  margin-top: 0.52rem;
  display: grid;
  gap: 0.56rem;
}

.section-card {
  padding: 0.46rem 0.52rem;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 24%, var(--ui-border-soft));
  border-radius: 10px;
}

.section-card--done {
  background: color-mix(in srgb, #ffffff 66%, var(--ui-accent-2-soft));
}

.section-card--risk {
  background: color-mix(in srgb, #ffffff 64%, var(--ui-highlight-soft));
}

.section-title {
  margin: 0 0 0.2rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: color-mix(in srgb, var(--ui-accent-deep) 84%, var(--ui-accent-2));
}

.line-list {
  margin: 0;
  padding: 0.42rem 0.56rem 0.42rem 1.15rem;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 28%, var(--ui-border-soft));
  background: color-mix(in srgb, #ffffff 66%, var(--ui-accent-2-soft));
  color: var(--ui-text-primary);
  display: grid;
  gap: 0.2rem;
}

.fallback {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
  padding: 0.42rem 0.56rem;
  border-radius: 10px;
  border: 1px dashed color-mix(in srgb, var(--ui-accent-2) 30%, var(--ui-border-soft));
  color: var(--ui-text-secondary);
  background: color-mix(in srgb, #ffffff 72%, var(--ui-accent-2-soft));
}

.feedback-box {
  margin-top: 0.52rem;
  padding: 0.48rem 0.6rem;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 36%, var(--ui-border-soft));
  border-radius: 10px;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, #ffffff 66%, var(--ui-accent-2-soft)) 0%,
    color-mix(in srgb, #ffffff 84%, var(--ui-accent-soft)) 100%
  );
}

.feedback-label {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.76rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: color-mix(in srgb, var(--ui-accent-deep) 82%, var(--ui-accent-2));
}

.feedback-text {
  margin: 0.18rem 0 0;
  color: var(--ui-text-primary);
  white-space: pre-wrap;
  line-height: 1.45;
}
</style>
