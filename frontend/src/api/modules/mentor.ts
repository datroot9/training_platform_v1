import { ApiError, requestJson } from '../client'
import type {
  AssignmentResponse,
  AssignmentTaskResponse,
  CreateTraineeResponse,
  CurriculumDetailResponse,
  CurriculumResponse,
  DailyReportResponse,
  LearningMaterialResponse,
  PagedResponse,
  ReviewWeeklySummaryRequest,
  TaskTemplateResponse,
  TraineeResponse,
  WeeklySummaryGenerationPlaceholderResponse,
  WeeklySummaryResponse,
} from '../types'

type SortDirection = 'asc' | 'desc'

type ListTraineesParams = {
  q?: string
  active?: boolean
  page?: number
  size?: number
  sortBy?: 'createdAt' | 'fullName' | 'email'
  sortDir?: SortDirection
}

type CurriculumStatus = 'DRAFT' | 'PUBLISHED'

type ListCurriculaParams = {
  q?: string
  status?: CurriculumStatus
  page?: number
  size?: number
  sortBy?: 'updatedAt' | 'createdAt' | 'name' | 'status' | 'publishedAt' | 'versionLabel'
  sortDir?: SortDirection
}

function buildQuery(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === '') continue
    search.set(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export async function listTrainees(params: ListTraineesParams = {}): Promise<PagedResponse<TraineeResponse>> {
  const q = buildQuery({
    q: params.q?.trim() || undefined,
    active: params.active,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return requestJson<PagedResponse<TraineeResponse>>(`/api/mentor/trainees${q}`)
}

export async function listActiveTrainees(
  params: Omit<ListTraineesParams, 'active'> = {},
): Promise<PagedResponse<TraineeResponse>> {
  return listTrainees({ ...params, active: true })
}

export async function createTrainee(body: { email: string; fullName: string }): Promise<CreateTraineeResponse> {
  return requestJson<CreateTraineeResponse>('/api/mentor/trainees', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function setTraineeActive(traineeId: number, active: boolean): Promise<void> {
  await requestJson<Record<string, unknown>>(`/api/mentor/trainees/${traineeId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  })
}

export async function resetTraineePassword(traineeId: number): Promise<{
  userId: number
  email: string
  temporaryPassword: string
  mustChangePassword: boolean
}> {
  return requestJson(`/api/mentor/trainees/${traineeId}/reset-password`, { method: 'POST' })
}

export async function assignCurriculum(
  traineeId: number,
  curriculumId: number,
): Promise<AssignmentResponse> {
  return requestJson<AssignmentResponse>(`/api/mentor/trainees/${traineeId}/assignments`, {
    method: 'POST',
    body: JSON.stringify({ curriculumId }),
  })
}

export async function replaceActiveAssignment(
  traineeId: number,
  curriculumId: number,
): Promise<AssignmentResponse> {
  return requestJson<AssignmentResponse>(`/api/mentor/trainees/${traineeId}/assignments/active`, {
    method: 'PUT',
    body: JSON.stringify({ curriculumId }),
  })
}

export async function getTraineeActiveAssignment(traineeId: number): Promise<AssignmentResponse> {
  return requestJson<AssignmentResponse>(`/api/mentor/trainees/${traineeId}/assignments/active`)
}

/** Returns null when the trainee has no active assignment (HTTP 404 from API). */
export async function getTraineeActiveAssignmentOrNull(traineeId: number): Promise<AssignmentResponse | null> {
  try {
    return await getTraineeActiveAssignment(traineeId)
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 404) return null
    throw e
  }
}

/** All assignments for the trainee (any status), newest first. */
export async function listTraineeAssignments(traineeId: number): Promise<AssignmentResponse[]> {
  return requestJson<AssignmentResponse[]>(`/api/mentor/trainees/${traineeId}/assignments`)
}

export async function getTraineeAssignmentTasks(
  traineeId: number,
  assignmentId: number,
): Promise<AssignmentTaskResponse[]> {
  return requestJson<AssignmentTaskResponse[]>(
    `/api/mentor/trainees/${traineeId}/assignments/${assignmentId}/tasks`,
  )
}

export async function listCurricula(params: ListCurriculaParams = {}): Promise<PagedResponse<CurriculumResponse>> {
  const q = buildQuery({
    q: params.q?.trim() || undefined,
    status: params.status,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return requestJson<PagedResponse<CurriculumResponse>>(`/api/mentor/curricula${q}`)
}

export async function listAllCurricula(params: Omit<ListCurriculaParams, 'page'> = {}): Promise<CurriculumResponse[]> {
  const size = params.size ?? 100
  let page = 0
  let totalPages = 1
  const items: CurriculumResponse[] = []

  while (page < totalPages) {
    const res = await listCurricula({ ...params, page, size })
    items.push(...res.items)
    totalPages = res.totalPages
    page += 1
  }

  return items
}

export async function createCurriculum(body: { name: string; description: string }): Promise<CurriculumResponse> {
  return requestJson<CurriculumResponse>('/api/mentor/curricula', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function getCurriculum(id: number): Promise<CurriculumDetailResponse> {
  return requestJson<CurriculumDetailResponse>(`/api/mentor/curricula/${id}`)
}

export async function updateCurriculum(
  id: number,
  body: { name?: string; description?: string },
): Promise<CurriculumResponse> {
  return requestJson<CurriculumResponse>(`/api/mentor/curricula/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function deleteCurriculumDraft(curriculumId: number): Promise<void> {
  await requestJson<null>(`/api/mentor/curricula/${curriculumId}`, {
    method: 'DELETE',
  })
}

export async function uploadMaterial(
  curriculumId: number,
  file: File,
  sortOrder?: number,
): Promise<LearningMaterialResponse> {
  const q = sortOrder != null ? `?sortOrder=${sortOrder}` : ''
  const form = new FormData()
  form.set('file', file)
  return requestJson<LearningMaterialResponse>(`/api/mentor/curricula/${curriculumId}/materials${q}`, {
    method: 'POST',
    body: form,
  })
}

export async function deleteMaterial(curriculumId: number, materialId: number): Promise<void> {
  await requestJson<null>(`/api/mentor/curricula/${curriculumId}/materials/${materialId}`, {
    method: 'DELETE',
  })
}

export async function createTaskTemplate(
  curriculumId: number,
  body: {
    title: string
    description?: string
    estimatedDays?: number
    sortOrder?: number
    learningMaterialId?: number
  },
): Promise<TaskTemplateResponse> {
  return requestJson<TaskTemplateResponse>(`/api/mentor/curricula/${curriculumId}/task-templates`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateTaskTemplate(
  curriculumId: number,
  templateId: number,
  body: {
    title?: string
    description?: string
    estimatedDays?: number
    sortOrder?: number
    learningMaterialId?: number | null
  },
): Promise<TaskTemplateResponse> {
  return requestJson<TaskTemplateResponse>(
    `/api/mentor/curricula/${curriculumId}/task-templates/${templateId}`,
    {
      method: 'PATCH',
      body: JSON.stringify(body),
    },
  )
}

export async function deleteTaskTemplate(curriculumId: number, templateId: number): Promise<void> {
  await requestJson<null>(`/api/mentor/curricula/${curriculumId}/task-templates/${templateId}`, {
    method: 'DELETE',
  })
}

export async function publishCurriculum(curriculumId: number): Promise<CurriculumResponse> {
  return requestJson<CurriculumResponse>(`/api/mentor/curricula/${curriculumId}/publish`, {
    method: 'POST',
  })
}

export async function createCurriculumVersion(
  sourceCurriculumId: number,
  body: { versionLabel: string; name?: string; description?: string },
): Promise<CurriculumResponse> {
  return requestJson<CurriculumResponse>(`/api/mentor/curricula/${sourceCurriculumId}/versions`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function listWeeklySummariesForTrainee(
  traineeId: number,
  assignmentId: number,
): Promise<WeeklySummaryResponse[]> {
  return requestJson<WeeklySummaryResponse[]>(
    `/api/mentor/trainees/${traineeId}/assignments/${assignmentId}/weekly-summaries`,
  )
}

export async function listDailyReportsForTraineeByRange(
  traineeId: number,
  assignmentId: number,
  fromDate: string,
  toDate: string,
): Promise<DailyReportResponse[]> {
  const q = buildQuery({ fromDate, toDate })
  return requestJson<DailyReportResponse[]>(
    `/api/mentor/trainees/${traineeId}/assignments/${assignmentId}/daily-reports${q}`,
  )
}

export async function reviewWeeklySummary(
  traineeId: number,
  assignmentId: number,
  weekStart: string,
  body: ReviewWeeklySummaryRequest,
): Promise<WeeklySummaryResponse> {
  return requestJson<WeeklySummaryResponse>(
    `/api/mentor/trainees/${traineeId}/assignments/${assignmentId}/weekly-summaries/${encodeURIComponent(weekStart)}/review`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    },
  )
}

export async function generateWeeklySummaryPlaceholder(
  traineeId: number,
  assignmentId: number,
): Promise<WeeklySummaryGenerationPlaceholderResponse> {
  return requestJson<WeeklySummaryGenerationPlaceholderResponse>(
    `/api/mentor/trainees/${traineeId}/assignments/${assignmentId}/weekly-summaries/generate`,
    { method: 'POST' },
  )
}
