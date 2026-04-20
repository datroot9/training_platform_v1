import { computed, inject, ref, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../api/client'
import * as traineeApi from '../api/modules/trainee'
import type { AssignmentResponse, AssignmentTaskResponse } from '../api/types'

export type TaskStatusTagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'

export interface TraineeAssignmentContext {
  assignment: Ref<AssignmentResponse | null>
  tasks: Ref<AssignmentTaskResponse[]>
  loading: Ref<boolean>
  error: Ref<string>
  hasAssignment: ComputedRef<boolean>
  completedTaskCount: ComputedRef<number>
  totalTaskCount: ComputedRef<number>
  progressPercent: ComputedRef<number>
  load: () => Promise<void>
  downloadPdf: (materialId: number) => Promise<void>
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

export function useTraineeAssignment(): TraineeAssignmentContext {
  const toast = useToast()
  const assignment = ref<AssignmentResponse | null>(null)
  const tasks = ref<AssignmentTaskResponse[]>([])
  const loading = ref(true)
  const error = ref('')

  const hasAssignment = computed(() => assignment.value != null)

  const completedTaskCount = computed(() => tasks.value.filter((t) => t.status === 'DONE').length)

  const totalTaskCount = computed(() => tasks.value.length)

  const progressPercent = computed(() => {
    const total = totalTaskCount.value
    if (total === 0) return 0
    return Math.round((completedTaskCount.value / total) * 100)
  })

  async function load(): Promise<void> {
    error.value = ''
    loading.value = true
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
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      a.click()
      URL.revokeObjectURL(url)
      toast.add({ severity: 'success', summary: 'Download started', detail: fileName, life: 2800 })
    } catch (e) {
      const detail = e instanceof Error ? e.message : 'Download failed'
      toast.add({ severity: 'error', summary: 'Download failed', detail, life: 4500 })
    }
  }

  return {
    assignment,
    tasks,
    loading,
    error,
    hasAssignment,
    completedTaskCount,
    totalTaskCount,
    progressPercent,
    load,
    downloadPdf,
  }
}
