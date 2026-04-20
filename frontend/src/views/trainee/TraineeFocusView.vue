<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import PageHeader from '../../components/layout/PageHeader.vue'
import {
  allowedTaskStatusTargets,
  injectTraineeAssignment,
  taskStatusLabel,
  taskStatusTagSeverity,
} from '../../composables/useTraineeAssignment'
import type { AssignmentTaskResponse, TaskStatus } from '../../api/types'

const {
  assignment,
  tasks,
  loading,
  error,
  hasAssignment,
  completedTaskCount,
  totalTaskCount,
  progressPercent,
  updatingTaskIds,
  downloadPdf,
  setTaskStatus,
} = injectTraineeAssignment()

const nextTask = computed(() => {
  const list = tasks.value
  return list.find((t) => t.status !== 'DONE') ?? null
})

const upcomingTasks = computed(() => {
  const list = tasks.value
  const idx = list.findIndex((t) => t.status !== 'DONE')
  if (idx < 0) return []
  return list.slice(idx + 1, idx + 4)
})

const allDone = computed(() => hasAssignment.value && tasks.value.length > 0 && nextTask.value == null)

const mentorNameDisplay = computed(() => assignment.value?.mentorName?.trim() || '-')
const mentorEmailDisplay = computed(() => assignment.value?.mentorEmail?.trim() || '-')
const estimatedDaysDisplay = computed(() => assignment.value?.totalEstimatedDays)

const orderedTasks = computed(() => [...tasks.value].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id))

function statusActionLabel(status: TaskStatus): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'Mark in progress'
    case 'DONE':
      return 'Mark done'
    case 'NOT_STARTED':
      return 'Mark not started'
    default:
      return status
  }
}

function statusActionSeverity(status: TaskStatus): 'success' | 'secondary' {
  return status === 'DONE' ? 'success' : 'secondary'
}

function statusTargets(task: AssignmentTaskResponse): TaskStatus[] {
  return allowedTaskStatusTargets(task.status)
}

async function changeTaskStatus(taskId: number, target: TaskStatus): Promise<void> {
  try {
    await setTaskStatus(taskId, target)
  } catch {
    // Error message is already surfaced by the shared composable.
  }
}
</script>

<template>
  <div class="wrap">
    <PageHeader
      title="My assignment"
      description="Focus on your next step and what is coming up. Large actions so you can continue quickly."
      :tag-value="hasAssignment ? `${progressPercent}% complete` : undefined"
      tag-severity="info"
    />

    <Message v-if="error" severity="error" :closable="false" class="mb-msg">{{ error }}</Message>

    <div v-if="loading" class="centered">
      <ProgressSpinner stroke-width="3" animation-duration=".8s" />
    </div>

    <template v-else-if="!hasAssignment">
      <Message severity="info" :closable="false">
        No active assignment. Ask your mentor to assign a published curriculum when you are ready to start.
      </Message>
    </template>

    <template v-else-if="allDone">
      <Message severity="success" :closable="false">
        You completed all {{ totalTaskCount }} tasks for <strong>{{ assignment!.curriculumName }}</strong>. Great work.
      </Message>
    </template>

    <template v-else>
      <section class="meta-card">
        <h2 class="meta-title">{{ assignment!.curriculumName }}</h2>
        <p class="meta-desc">
          {{ assignment!.curriculumDescription?.trim() || 'No curriculum description provided by your mentor yet.' }}
        </p>
        <div class="meta-grid">
          <p><strong>Mentor:</strong> {{ mentorNameDisplay }}</p>
          <p><strong>Mentor email:</strong> {{ mentorEmailDisplay }}</p>
          <p>
            <strong>Estimated completion:</strong>
            <span v-if="estimatedDaysDisplay != null">{{ estimatedDaysDisplay }} days</span>
            <span v-else>Not set</span>
          </p>
        </div>
      </section>

      <p class="summary">
        <span class="muted">{{ completedTaskCount }} of {{ totalTaskCount }} tasks done</span>
      </p>

      <section v-if="nextTask" class="hero">
        <p class="eyebrow">Next up</p>
        <Card class="hero-card">
          <template #title>
            <div class="hero-title-row">
              <span>{{ nextTask.title }}</span>
              <Tag
                :value="taskStatusLabel(nextTask.status)"
                :severity="taskStatusTagSeverity(nextTask.status)"
                rounded
              />
            </div>
          </template>
          <template #content>
            <p v-if="nextTask.description" class="desc">{{ nextTask.description }}</p>
            <p class="estimate">Estimated: {{ nextTask.estimatedDays != null ? `${nextTask.estimatedDays} day(s)` : 'Not set' }}</p>
            <div class="hero-actions">
              <div class="status-actions">
                <Button
                  v-for="target in statusTargets(nextTask)"
                  :key="`next-${target}`"
                  :label="statusActionLabel(target)"
                  :severity="statusActionSeverity(target)"
                  :loading="updatingTaskIds.has(nextTask.id)"
                  :disabled="updatingTaskIds.has(nextTask.id)"
                  size="small"
                  outlined
                  @click="changeTaskStatus(nextTask.id, target)"
                />
              </div>
              <Button
                v-if="nextTask.learningMaterialId != null"
                label="Download PDF for this step"
                icon="pi pi-download"
                size="large"
                @click="downloadPdf(nextTask.learningMaterialId)"
              />
              <p v-else class="muted small">This step has no PDF. Continue when you have finished the activity.</p>
            </div>
          </template>
        </Card>
      </section>

      <section v-if="upcomingTasks.length" class="upcoming">
        <h2 class="section-title">Coming up</h2>
        <div class="upcoming-grid">
          <Card v-for="t in upcomingTasks" :key="t.id" class="mini-card">
            <template #title>
              <span class="mini-title">{{ t.title }}</span>
            </template>
            <template #content>
              <Tag :value="taskStatusLabel(t.status)" :severity="taskStatusTagSeverity(t.status)" class="mb-tag" />
              <p class="estimate compact">
                Estimated: {{ t.estimatedDays != null ? `${t.estimatedDays} day(s)` : 'Not set' }}
              </p>
              <div class="status-actions compact">
                <Button
                  v-for="target in statusTargets(t)"
                  :key="`up-${t.id}-${target}`"
                  :label="statusActionLabel(target)"
                  :severity="statusActionSeverity(target)"
                  :loading="updatingTaskIds.has(t.id)"
                  :disabled="updatingTaskIds.has(t.id)"
                  size="small"
                  text
                  @click="changeTaskStatus(t.id, target)"
                />
              </div>
              <Button
                v-if="t.learningMaterialId != null"
                label="PDF"
                icon="pi pi-file-pdf"
                size="small"
                text
                @click="downloadPdf(t.learningMaterialId)"
              />
            </template>
          </Card>
        </div>
      </section>

      <section class="all-tasks">
        <h2 class="section-title">All tasks</h2>
        <Card v-for="task in orderedTasks" :key="`all-${task.id}`" class="task-row">
          <template #title>
            <div class="task-row-title">
              <span>Step {{ task.sortOrder }} · {{ task.title }}</span>
              <Tag :value="taskStatusLabel(task.status)" :severity="taskStatusTagSeverity(task.status)" rounded />
            </div>
          </template>
          <template #content>
            <p v-if="task.description" class="desc compact">{{ task.description }}</p>
            <p class="estimate compact">
              Estimated: {{ task.estimatedDays != null ? `${task.estimatedDays} day(s)` : 'Not set' }}
            </p>
            <div class="task-row-actions">
              <div class="status-actions">
                <Button
                  v-for="target in statusTargets(task)"
                  :key="`all-status-${task.id}-${target}`"
                  :label="statusActionLabel(target)"
                  :severity="statusActionSeverity(target)"
                  :loading="updatingTaskIds.has(task.id)"
                  :disabled="updatingTaskIds.has(task.id)"
                  size="small"
                  outlined
                  @click="changeTaskStatus(task.id, target)"
                />
              </div>
              <Button
                v-if="task.learningMaterialId != null"
                label="Download PDF"
                icon="pi pi-download"
                size="small"
                text
                @click="downloadPdf(task.learningMaterialId)"
              />
            </div>
          </template>
        </Card>
      </section>
    </template>
  </div>
</template>

<style scoped>
.wrap {
  max-width: 48rem;
}
.mb-msg {
  margin-top: 0.75rem;
}
.centered {
  display: flex;
  justify-content: center;
  padding: 2rem;
}
.summary {
  margin: 0.25rem 0 1.25rem;
  font-size: 1rem;
}
.meta-card {
  border: 1px solid #ddd6fe;
  background: #faf5ff;
  border-radius: 12px;
  padding: 0.9rem 1rem;
  margin: 0.25rem 0 1rem;
}
.meta-title {
  margin: 0;
  font-size: 1.1rem;
}
.meta-desc {
  margin: 0.35rem 0 0.65rem;
  color: #475569;
  line-height: 1.45;
}
.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.35rem 0.9rem;
}
.meta-grid p {
  margin: 0;
  font-size: 0.92rem;
}
.muted {
  color: var(--text-muted);
}
.small {
  font-size: 0.9rem;
  margin: 0;
}
.hero {
  margin-bottom: 2rem;
}
.eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--trainee-accent, #6d28d9);
}
.hero-card {
  border: 2px solid #ddd6fe;
  box-shadow: 0 8px 24px rgba(109, 40, 217, 0.08);
}
.hero-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  font-size: 1.25rem;
  line-height: 1.35;
}
.desc {
  margin: 0 0 1rem;
  color: #475569;
  line-height: 1.55;
  font-size: 1rem;
}
.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}
.estimate {
  margin: 0 0 0.65rem;
  color: #475569;
  font-size: 0.92rem;
}
.estimate.compact {
  margin-bottom: 0.45rem;
  font-size: 0.86rem;
}
.status-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.status-actions.compact {
  margin-bottom: 0.35rem;
}
.section-title {
  margin: 0 0 0.75rem;
  font-size: 1.1rem;
  font-weight: 600;
  color: #334155;
}
.upcoming-grid {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}
.mini-card {
  border: 1px solid #e2e8f0;
  box-shadow: none;
}
.mini-title {
  font-size: 0.95rem;
  font-weight: 600;
}
.mb-tag {
  margin-bottom: 0.35rem;
}
.all-tasks {
  margin-top: 1rem;
}
.task-row {
  border: 1px solid #e2e8f0;
  box-shadow: none;
  margin-bottom: 0.75rem;
}
.task-row-title {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: center;
  flex-wrap: wrap;
}
.task-row-actions {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
  align-items: center;
}
</style>
