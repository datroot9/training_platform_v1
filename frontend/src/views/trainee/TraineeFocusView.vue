<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import PageHeader from '../../components/layout/PageHeader.vue'
import {
  injectTraineeAssignment,
  taskPrimaryAction,
  taskStatusLabel,
  taskStatusTagSeverity,
} from '../../composables/useTraineeAssignment'
import type { AssignmentTaskResponse } from '../../api/types'

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
  previewFileName,
  previewUrl,
  previewLoading,
  previewError,
  setTaskStatus,
  loadMaterialPreview,
  clearMaterialPreview,
  downloadPreviewedPdf,
} = injectTraineeAssignment()

const orderedTasks = computed(() => [...tasks.value].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id))
const lastCompletedIndex = computed(() =>
  orderedTasks.value.reduce((last, task, index) => (task.status === 'DONE' ? index : last), -1),
)
const selectedTaskId = ref<number | null>(null)
const pdfShellRef = ref<HTMLElement | null>(null)
const isPdfFullscreen = ref(false)

const selectedTask = computed<AssignmentTaskResponse | null>(() => {
  if (selectedTaskId.value == null) return null
  return orderedTasks.value.find((task) => task.id === selectedTaskId.value) ?? null
})

const mentorNameDisplay = computed(() => assignment.value?.mentorName?.trim() || '-')
const mentorEmailDisplay = computed(() => assignment.value?.mentorEmail?.trim() || '-')
const estimatedDaysDisplay = computed(() => assignment.value?.totalEstimatedDays)
const curriculumVersionDisplay = computed(() => assignment.value?.curriculumVersionLabel?.trim() || null)

const mentorAvatarLabel = computed(() =>
  initialsFromMentor(assignment.value?.mentorName, assignment.value?.mentorEmail),
)

function initialsFromMentor(name?: string | null, email?: string | null): string {
  const n = name?.trim()
  if (n) {
    const parts = n.split(/\s+/).filter(Boolean)
    if (parts.length >= 2) {
      const a = parts[0][0]
      const b = parts[parts.length - 1][0]
      if (a && b) return (a + b).toUpperCase()
    }
    if (parts.length === 1) {
      const w = parts[0]
      if (w.length >= 2) return w.slice(0, 2).toUpperCase()
      if (w.length === 1) return w.toUpperCase()
    }
  }
  const e = email?.trim()
  if (e?.includes('@')) {
    const local = e.split('@')[0] ?? ''
    if (local.length >= 2) return local.slice(0, 2).toUpperCase()
    if (local.length === 1) return local.toUpperCase()
  }
  return '?'
}
const allDone = computed(() => hasAssignment.value && tasks.value.length > 0 && completedTaskCount.value === totalTaskCount.value)

const selectedPrimaryAction = computed(() => {
  if (!selectedTask.value) return null
  return taskPrimaryAction(selectedTask.value.status)
})

const previewIframeSrc = computed(() => {
  if (!previewUrl.value) return ''
  // Best-effort: ask browser PDF viewer to hide native toolbar controls.
  return `${previewUrl.value}#toolbar=0&navpanes=0&statusbar=0&messages=0`
})

function buildTaskPdfName(title?: string | null): string {
  const raw = (title ?? '').trim()
  if (!raw) return 'learning-material.pdf'
  const sanitized = raw
    .replace(/[\\/:*?"<>|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120)
  if (!sanitized) return 'learning-material.pdf'
  return `${sanitized}.pdf`
}

function syncFullscreenState(): void {
  isPdfFullscreen.value = document.fullscreenElement === pdfShellRef.value
}

async function togglePdfFullscreen(): Promise<void> {
  const shell = pdfShellRef.value
  if (!shell) return
  try {
    if (document.fullscreenElement === shell) {
      await document.exitFullscreen()
      return
    }
    await shell.requestFullscreen()
  } catch {
    // Browser denied fullscreen request (gesture or policy).
  }
}

watch(
  orderedTasks,
  (list) => {
    if (!list.length) {
      selectedTaskId.value = null
      clearMaterialPreview()
      return
    }
    const stillExists = selectedTaskId.value != null && list.some((task) => task.id === selectedTaskId.value)
    if (!stillExists) {
      selectedTaskId.value = list.find((task) => task.status !== 'DONE')?.id ?? list[0].id
    }
  },
  { immediate: true },
)

watch(
  selectedTask,
  async (task) => {
    if (!task || task.learningMaterialId == null) {
      clearMaterialPreview()
      return
    }
    await loadMaterialPreview(task.learningMaterialId, buildTaskPdfName(task.title))
  },
  { immediate: true },
)

function selectTask(taskId: number): void {
  selectedTaskId.value = taskId
}

function isTimelineLineActive(index: number): boolean {
  return index <= lastCompletedIndex.value
}

async function runPrimaryAction(task: AssignmentTaskResponse): Promise<void> {
  const action = taskPrimaryAction(task.status)
  try {
    await setTaskStatus(task.id, action.target)
  } catch {
    // Shared composable already sets surface error message.
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState)
})
</script>

<template>
  <div class="wrap">
    <PageHeader
      title="My assignment"
      description="Work through your queue and review learning material side by side."
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

    <div v-else class="focus-grid">
      <section class="task-pane">
        <section class="meta-card" aria-label="Curriculum overview">
          <div class="meta-card__top">
            <div class="meta-card__headline">
              <p class="meta-card__eyebrow">Your curriculum</p>
              <h2 class="meta-card__title">{{ assignment!.curriculumName }}</h2>
            </div>
            <div class="meta-card__chips">
              <Tag
                v-if="curriculumVersionDisplay"
                :value="`Version ${curriculumVersionDisplay}`"
                severity="secondary"
                rounded
              />
              <Tag
                v-if="estimatedDaysDisplay != null"
                :value="`${estimatedDaysDisplay} day timeline`"
                severity="info"
                rounded
              />
              <Tag v-else value="Timeline not set" severity="contrast" rounded />
            </div>
          </div>
          <p class="meta-card__desc">
            {{ assignment!.curriculumDescription?.trim() || 'No curriculum description provided by your mentor yet.' }}
          </p>
          <div class="meta-card__mentor-row">
            <Avatar class="meta-card__avatar" :label="mentorAvatarLabel" size="large" shape="circle" />
            <div class="meta-card__mentor-body">
              <span class="meta-card__mentor-badge">Mentor</span>
              <p class="meta-card__mentor-name">{{ mentorNameDisplay }}</p>
              <p class="meta-card__mentor-email">{{ mentorEmailDisplay }}</p>
            </div>
          </div>
        </section>

        <p class="summary">{{ completedTaskCount }} of {{ totalTaskCount }} tasks done</p>
        <Message v-if="allDone" severity="success" :closable="false" class="mb-msg">
          Great work. You completed all tasks for this curriculum.
        </Message>

        <Card v-if="selectedTask" class="focus-card">
          <template #title>
            <div class="focus-title-row">
              <span>Step {{ selectedTask.sortOrder }} · {{ selectedTask.title }}</span>
              <Tag :value="taskStatusLabel(selectedTask.status)" :severity="taskStatusTagSeverity(selectedTask.status)" rounded />
            </div>
          </template>
          <template #content>
            <p v-if="selectedTask.description" class="desc">{{ selectedTask.description }}</p>
            <div class="focus-actions">
              <Button
                v-if="selectedPrimaryAction"
                :label="selectedPrimaryAction.label"
                :severity="selectedPrimaryAction.severity"
                :loading="updatingTaskIds.has(selectedTask.id)"
                :disabled="updatingTaskIds.has(selectedTask.id)"
                @click="runPrimaryAction(selectedTask)"
              />
            </div>
          </template>
        </Card>

        <section class="queue">
          <h3 class="queue-title">Task queue</h3>
          <div class="queue-list">
            <button
              v-for="(task, index) in orderedTasks"
              :key="task.id"
              class="queue-item"
              :class="{
                active: task.id === selectedTaskId,
                done: task.status === 'DONE',
                current: task.status === 'IN_PROGRESS',
                upcoming: task.status === 'NOT_STARTED',
              }"
              type="button"
              @click="selectTask(task.id)"
            >
              <div class="queue-rail" aria-hidden="true">
                <span class="queue-line queue-line-top" :class="{ active: isTimelineLineActive(index - 1) }" />
                <span class="queue-node" />
                <span class="queue-line queue-line-bottom" :class="{ active: isTimelineLineActive(index) }" />
              </div>
              <div class="queue-item-body">
                <div class="queue-item-top">
                  <span class="queue-label">Step {{ task.sortOrder }}</span>
                  <Tag :value="taskStatusLabel(task.status)" :severity="taskStatusTagSeverity(task.status)" />
                </div>
                <p class="queue-name">{{ task.title }}</p>
                <p class="queue-meta">
                  {{
                    task.estimatedDays != null
                      ? `${task.estimatedDays} day(s) estimate`
                      : task.learningMaterialId != null
                        ? 'PDF available'
                        : 'No estimate'
                  }}
                </p>
              </div>
            </button>
          </div>
        </section>
      </section>

      <aside class="material-pane">
        <div class="material-head">
          <h3 class="material-title">Learning material</h3>
          <div class="material-actions">
            <Button
              label="Download"
              icon="pi pi-download"
              text
              size="small"
              :disabled="!previewUrl || previewLoading"
              @click="downloadPreviewedPdf"
            />
            <Button
              :label="isPdfFullscreen ? 'Exit full screen' : 'Full screen'"
              :icon="isPdfFullscreen ? 'pi pi-window-minimize' : 'pi pi-window-maximize'"
              text
              size="small"
              :disabled="!previewUrl || previewLoading"
              @click="togglePdfFullscreen"
            />
          </div>
        </div>

        <div v-if="previewLoading" class="material-loading">
          <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.2rem; height: 2.2rem" />
          <p class="muted">Loading PDF...</p>
        </div>
        <Message v-else-if="previewError" severity="error" :closable="false">{{ previewError }}</Message>
        <Message v-else-if="!selectedTask" severity="info" :closable="false">
          Select a task to review its details and learning material.
        </Message>
        <Message v-else-if="selectedTask.learningMaterialId == null" severity="info" :closable="false">
          This task has no PDF material attached.
        </Message>
        <div v-else-if="previewUrl" ref="pdfShellRef" class="pdf-wrap">
          <p class="pdf-name">{{ previewFileName || 'learning-material.pdf' }}</p>
          <iframe :src="previewIframeSrc" title="Learning material preview" class="pdf-frame" />
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
}

.mb-msg {
  margin-top: 0.75rem;
}

.centered {
  display: flex;
  justify-content: center;
  padding: 2rem;
}

.focus-grid {
  margin-top: 0.25rem;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 1rem;
  align-items: start;
}

.task-pane,
.material-pane {
  background: var(--ui-surface);
  border: 1px solid var(--ui-border);
  border-radius: 12px;
  padding: 1.05rem;
  box-shadow: var(--ui-shadow-md);
}

.meta-card {
  border: 1px solid var(--ui-border);
  background: linear-gradient(135deg, #ffffff 0%, var(--ui-surface-tint) 54%, var(--ui-coral-soft) 100%);
  border-radius: var(--ui-radius-md);
  padding: 1.1rem 1.2rem 1.1rem;
  box-shadow: var(--ui-shadow-md);
}

.meta-card__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.65rem 1rem;
}

.meta-card__headline {
  min-width: 0;
  flex: 1 1 200px;
}

.meta-card__eyebrow {
  margin: 0 0 0.2rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ui-accent-2);
}

.meta-card__title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 700;
  line-height: 1.25;
  color: var(--ui-heading);
  letter-spacing: -0.01em;
}

.meta-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  justify-content: flex-end;
  flex: 0 1 auto;
}

.meta-card__desc {
  margin: 0.65rem 0 0.85rem;
  color: var(--ui-text-secondary);
  line-height: 1.5;
  font-size: 0.94rem;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-card__mentor-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding-top: 0.65rem;
  margin-top: 0.1rem;
  border-top: 1px solid var(--ui-border-soft);
}

.meta-card__avatar {
  flex-shrink: 0;
  background: linear-gradient(145deg, var(--tp-purple-500), var(--ui-coral));
  color: #ffffff;
  font-weight: 700;
  font-size: 0.95rem;
}

.meta-card__mentor-body {
  min-width: 0;
}

.meta-card__mentor-badge {
  display: inline-block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ui-text-secondary);
  margin-bottom: 0.15rem;
}

.meta-card__mentor-name {
  margin: 0;
  font-weight: 600;
  font-size: 1rem;
  color: var(--ui-heading);
}

.meta-card__mentor-email {
  margin: 0.15rem 0 0;
  font-size: 0.86rem;
  color: var(--ui-text-secondary);
  word-break: break-word;
}

@media (max-width: 640px) {
  .meta-card__chips {
    justify-content: flex-start;
  }
}

.summary {
  margin: 0.7rem 0;
  font-weight: 600;
  color: var(--ui-text-primary);
}

.focus-card {
  margin-bottom: 0.85rem;
  border: 1px solid var(--ui-border);
  box-shadow: var(--ui-shadow-xs);
}

.focus-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.65rem;
  flex-wrap: wrap;
}

.desc {
  margin: 0 0 0.8rem;
  color: var(--ui-text-secondary);
  line-height: 1.5;
}

.estimate {
  margin: 0 0 0.8rem;
  color: var(--ui-text-secondary);
  font-size: 0.92rem;
}

.focus-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}

.queue-title {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.queue-item {
  border: none;
  background: transparent;
  border-radius: 12px;
  padding: 0;
  text-align: left;
  cursor: pointer;
  display: grid;
  grid-template-columns: 1.45rem minmax(0, 1fr);
  gap: 0.6rem;
  align-items: stretch;
  transition: transform 0.2s ease;
}

.queue-item:hover {
  transform: translateY(-1px);
}

.queue-item-top {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: center;
}

.queue-label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--ui-text-secondary);
}

.queue-name {
  margin: 0.42rem 0 0.2rem;
  color: var(--ui-heading);
  font-weight: 600;
}

.queue-meta {
  margin: 0;
  color: var(--ui-text-secondary);
  font-size: 0.82rem;
}

.queue-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.queue-line {
  width: 3px;
  flex: 1;
  background: var(--ui-border);
  border-radius: 999px;
  transition: background 0.25s ease;
}

.queue-line.active {
  background: linear-gradient(180deg, var(--tp-purple-500) 0%, var(--ui-coral) 100%);
}

.queue-line-top {
  margin-bottom: 0.18rem;
}

.queue-line-bottom {
  margin-top: 0.18rem;
}

.queue-item:first-child .queue-line-top,
.queue-item:last-child .queue-line-bottom {
  opacity: 0;
}

.queue-node {
  width: 0.72rem;
  height: 0.72rem;
  border-radius: 999px;
  border: 2px solid var(--ui-border);
  background: var(--ui-surface);
  box-shadow: 0 0 0 4px rgba(164, 53, 240, 0.08);
  transition: border-color 0.25s ease, background-color 0.25s ease, box-shadow 0.25s ease;
}

.queue-item.done .queue-node {
  background: var(--tp-purple-500);
  border-color: var(--tp-purple-500);
  box-shadow: 0 0 0 6px rgba(164, 53, 240, 0.2);
}

.queue-item.current .queue-node {
  background: var(--ui-accent-2);
  border-color: var(--ui-accent-2);
  box-shadow: 0 0 0 6px rgba(79, 70, 229, 0.22);
}

.queue-item-body {
  border: 1px solid var(--ui-border-soft);
  background: var(--ui-surface);
  border-radius: 12px;
  padding: 0.65rem 0.75rem;
  box-shadow: var(--ui-shadow-xs);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.queue-item:hover .queue-item-body {
  border-color: color-mix(in srgb, var(--ui-accent-2) 30%, var(--ui-border));
  box-shadow: var(--ui-shadow-sm);
  transform: translateY(-1px);
}

.queue-item.active .queue-item-body {
  border-color: var(--ui-accent-2);
  box-shadow: 0 0 0 3px var(--ui-focus-ring);
}

.material-head {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  align-items: center;
  margin-bottom: 0.6rem;
}

.material-actions {
  display: flex;
  align-items: center;
  gap: 0.15rem;
}

.material-title {
  margin: 0;
  font-size: 1rem;
}

.material-loading {
  min-height: 14rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
}

.pdf-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.pdf-name {
  margin: 0;
  font-size: 0.84rem;
  color: var(--ui-text-secondary);
}

.pdf-frame {
  width: 100%;
  min-height: min(65vh, 760px);
  border: 1px solid var(--ui-border-soft);
  border-radius: 8px;
  background: var(--ui-surface);
}

.pdf-wrap:fullscreen {
  width: 100vw;
  height: 100vh;
  background: #0f172a;
  padding: 0.75rem;
  gap: 0.65rem;
}

.pdf-wrap:fullscreen .pdf-name {
  color: #e2e8f0;
}

.pdf-wrap:fullscreen .pdf-frame {
  flex: 1;
  min-height: 0;
  border-color: #334155;
  border-radius: 6px;
}

.muted {
  color: var(--text-muted);
}

.small {
  margin: 0;
  font-size: 0.88rem;
}

@media (max-width: 1100px) {
  .focus-grid {
    grid-template-columns: 1fr;
  }

  .pdf-frame {
    min-height: 58vh;
  }
}
</style>
