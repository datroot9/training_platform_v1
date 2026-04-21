<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
            <p class="estimate">
              Estimated: {{ selectedTask.estimatedDays != null ? `${selectedTask.estimatedDays} day(s)` : 'Not set' }}
            </p>
            <div class="focus-actions">
              <Button
                v-if="selectedPrimaryAction"
                :label="selectedPrimaryAction.label"
                :severity="selectedPrimaryAction.severity"
                :loading="updatingTaskIds.has(selectedTask.id)"
                :disabled="updatingTaskIds.has(selectedTask.id)"
                @click="runPrimaryAction(selectedTask)"
              />
              <p v-if="selectedTask.learningMaterialId == null" class="muted small">
                This step has no PDF attached.
              </p>
            </div>
          </template>
        </Card>

        <section class="queue">
          <h3 class="queue-title">Task queue</h3>
          <div class="queue-list">
            <button
              v-for="task in orderedTasks"
              :key="task.id"
              class="queue-item"
              :class="{ active: task.id === selectedTaskId }"
              type="button"
              @click="selectTask(task.id)"
            >
              <div class="queue-item-top">
                <span class="queue-label">Step {{ task.sortOrder }}</span>
                <Tag :value="taskStatusLabel(task.status)" :severity="taskStatusTagSeverity(task.status)" />
              </div>
              <p class="queue-name">{{ task.title }}</p>
              <p class="queue-meta">{{ task.learningMaterialId != null ? 'PDF available' : 'No PDF' }}</p>
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
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #ddd6fe;
  border-radius: 12px;
  padding: 1rem;
}

.meta-card {
  border: 1px solid #ddd6fe;
  background: #faf5ff;
  border-radius: 12px;
  padding: 0.9rem 1rem;
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

.summary {
  margin: 0.7rem 0;
  font-weight: 600;
  color: #334155;
}

.focus-card {
  margin-bottom: 0.85rem;
  border: 1px solid #ddd6fe;
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
  color: #475569;
  line-height: 1.5;
}

.estimate {
  margin: 0 0 0.8rem;
  color: #475569;
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
  gap: 0.5rem;
}

.queue-item {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 10px;
  padding: 0.65rem 0.75rem;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.queue-item:hover {
  border-color: #c4b5fd;
  transform: translateY(-1px);
}

.queue-item.active {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.12);
}

.queue-item-top {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: center;
}

.queue-label {
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #64748b;
}

.queue-name {
  margin: 0.42rem 0 0.2rem;
  color: #1e293b;
  font-weight: 600;
}

.queue-meta {
  margin: 0;
  color: #64748b;
  font-size: 0.82rem;
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
  color: #64748b;
}

.pdf-frame {
  width: 100%;
  min-height: min(65vh, 760px);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
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
