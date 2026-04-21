import { computed, inject, onBeforeUnmount, ref, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../api/client'
import * as traineeApi from '../api/modules/trainee'
import type { AssignmentResponse, AssignmentTaskResponse, TaskStatus } from '../api/types'

export type TaskStatusTagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'
export type TaskActionButtonSeverity = 'success' | 'secondary'

export interface PrimaryTaskAction {
  target: TaskStatus
  label: string
  severity: TaskActionButtonSeverity
}

export interface TraineeAssignmentContext {
  assignment: Ref<AssignmentResponse | null>
  tasks: Ref<AssignmentTaskResponse[]>
  loading: Ref<boolean>
  error: Ref<string>
  hasAssignment: ComputedRef<boolean>
  completedTaskCount: ComputedRef<number>
  totalTaskCount: ComputedRef<number>
  progressPercent: ComputedRef<number>
  updatingTaskIds: Ref<Set<number>>
  previewMaterialId: Ref<number | null>
  previewFileName: Ref<string>
  previewUrl: Ref<string | null>
  previewLoading: Ref<boolean>
  previewError: Ref<string>
  load: () => Promise<void>
  downloadPdf: (materialId: number) => Promise<void>
  loadMaterialPreview: (materialId: number, preferredFileName?: string) => Promise<void>
  clearMaterialPreview: () => void
  downloadPreviewedPdf: () => Promise<void>
  setTaskStatus: (taskId: number, status: TaskStatus) => Promise<void>
}

export const traineeAssignmentContextKey: InjectionKey<TraineeAssignmentContext> = Symbol(
  'traineeAssignmentContext',
)

export function injectTraineeAssignment(): TraineeAssignmentContext {
  const ctx = inject(traineeAssignmentContextKey)
  if (!ctx) {
    throw new Error('injectTraineeAssignment() must be used within TraineeLayout')
  }
  return ctx
}

export function taskStatusLabel(status: string): string {
  switch (status) {
    case 'NOT_STARTED':
      return 'Not started'
    case 'IN_PROGRESS':
      return 'In progress'
    case 'DONE':
      return 'Done'
    default:
      return status
  }
}

export function taskStatusTagSeverity(status: string): TaskStatusTagSeverity {
  switch (status) {
    case 'DONE':
      return 'success'
    case 'IN_PROGRESS':
      return 'info'
    default:
      return 'secondary'
  }
}

export function allowedTaskStatusTargets(current: TaskStatus): TaskStatus[] {
  switch (current) {
    case 'NOT_STARTED':
      return ['IN_PROGRESS']
    case 'IN_PROGRESS':
      return ['NOT_STARTED', 'DONE']
    case 'DONE':
      return ['IN_PROGRESS']
    default:
      return []
  }
}

export function taskPrimaryAction(current: TaskStatus): PrimaryTaskAction {
  switch (current) {
    case 'NOT_STARTED':
      return { target: 'IN_PROGRESS', label: 'Start task', severity: 'secondary' }
    case 'IN_PROGRESS':
      return { target: 'DONE', label: 'Mark done', severity: 'success' }
    case 'DONE':
      return { target: 'IN_PROGRESS', label: 'Reopen task', severity: 'secondary' }
    default:
      return { target: 'IN_PROGRESS', label: 'Continue task', severity: 'secondary' }
  }
}

export function useTraineeAssignment(): TraineeAssignmentContext {
  const toast = useToast()
  const assignment = ref<AssignmentResponse | null>(null)
  const tasks = ref<AssignmentTaskResponse[]>([])
  const loading = ref(true)
  const error = ref('')
  const updatingTaskIds = ref<Set<number>>(new Set())
  const previewMaterialId = ref<number | null>(null)
  const previewFileName = ref('')
  const previewUrl = ref<string | null>(null)
  const previewLoading = ref(false)
  const previewError = ref('')
  const previewBlob = ref<Blob | null>(null)

  let previewRequestVersion = 0

  const hasAssignment = computed(() => assignment.value != null)

  const completedTaskCount = computed(() => tasks.value.filter((t) => t.status === 'DONE').length)

  const totalTaskCount = computed(() => tasks.value.length)

  const progressPercent = computed(() => {
    const total = totalTaskCount.value
    if (total === 0) return 0
    return Math.round((completedTaskCount.value / total) * 100)
  })

  function revokePreviewUrl(): void {
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
    }
    previewUrl.value = null
  }

  function clearMaterialPreview(): void {
    previewRequestVersion += 1
    revokePreviewUrl()
    previewBlob.value = null
    previewMaterialId.value = null
    previewFileName.value = ''
    previewError.value = ''
    previewLoading.value = false
  }

  function startBrowserDownload(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  }

  async function load(): Promise<void> {
    error.value = ''
    loading.value = true
    clearMaterialPreview()
    tasks.value = []
    assignment.value = null
    try {
      const a = await traineeApi.getActiveAssignmentOrNull()
      assignment.value = a
      if (a) {
        tasks.value = await traineeApi.getAssignmentTasks(a.id)
      }
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Could not load your assignment'
    } finally {
      loading.value = false
    }
  }

  async function downloadPdf(materialId: number): Promise<void> {
    try {
      const { blob, fileName } = await traineeApi.downloadMaterial(materialId)
      startBrowserDownload(blob, fileName)
      toast.add({ severity: 'success', summary: 'Download started', detail: fileName, life: 2800 })
    } catch (e) {
      const detail = e instanceof Error ? e.message : 'Download failed'
      toast.add({ severity: 'error', summary: 'Download failed', detail, life: 4500 })
    }
  }

  async function loadMaterialPreview(materialId: number, preferredFileName?: string): Promise<void> {
    if (materialId <= 0) {
      clearMaterialPreview()
      return
    }
    const requestVersion = ++previewRequestVersion
    previewLoading.value = true
    previewError.value = ''
    previewMaterialId.value = materialId
    try {
      const { blob, fileName } = await traineeApi.downloadMaterial(materialId)
      if (requestVersion !== previewRequestVersion) return
      revokePreviewUrl()
      const resolvedFileName =
        preferredFileName && preferredFileName.trim() ? preferredFileName.trim() : fileName
      const previewFile = new File([blob], resolvedFileName, { type: blob.type || 'application/pdf' })
      previewBlob.value = previewFile
      previewFileName.value = previewFile.name
      previewUrl.value = URL.createObjectURL(previewFile)
    } catch (e) {
      if (requestVersion !== previewRequestVersion) return
      previewBlob.value = null
      previewFileName.value = ''
      previewMaterialId.value = null
      revokePreviewUrl()
      previewError.value = e instanceof Error ? e.message : 'Could not load learning material'
    } finally {
      if (requestVersion === previewRequestVersion) {
        previewLoading.value = false
      }
    }
  }

  async function downloadPreviewedPdf(): Promise<void> {
    if (!previewBlob.value || !previewFileName.value) {
      toast.add({
        severity: 'warn',
        summary: 'No material selected',
        detail: 'Pick a task with a PDF first.',
        life: 2500,
      })
      return
    }
    startBrowserDownload(previewBlob.value, previewFileName.value)
    toast.add({ severity: 'success', summary: 'Download started', detail: previewFileName.value, life: 2800 })
  }

  async function setTaskStatus(taskId: number, status: TaskStatus): Promise<void> {
    if (!assignment.value) return
    const currentTask = tasks.value.find((t) => t.id === taskId)
    if (!currentTask || currentTask.status === status) return

    updatingTaskIds.value = new Set(updatingTaskIds.value).add(taskId)
    error.value = ''
    try {
      const updated = await traineeApi.updateTaskStatus(assignment.value.id, taskId, status)
      tasks.value = tasks.value.map((task) => (task.id === taskId ? updated : task))
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Could not update task status'
      throw e
    } finally {
      const next = new Set(updatingTaskIds.value)
      next.delete(taskId)
      updatingTaskIds.value = next
    }
  }

  onBeforeUnmount(() => {
    clearMaterialPreview()
  })

  return {
    assignment,
    tasks,
    loading,
    error,
    hasAssignment,
    completedTaskCount,
    totalTaskCount,
    progressPercent,
    updatingTaskIds,
    previewMaterialId,
    previewFileName,
    previewUrl,
    previewLoading,
    previewError,
    load,
    downloadPdf,
    loadMaterialPreview,
    clearMaterialPreview,
    downloadPreviewedPdf,
    setTaskStatus,
  }
}
