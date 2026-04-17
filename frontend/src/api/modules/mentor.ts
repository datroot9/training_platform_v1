import { requestJson } from '../client'
import type {
  AssignmentResponse,
  CreateTraineeResponse,
  CurriculumDetailResponse,
  CurriculumResponse,
  LearningMaterialResponse,
  TaskTemplateResponse,
  TraineeResponse,
} from '../types'

export async function listTrainees(query?: string): Promise<TraineeResponse[]> {
  const q = query?.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''
  return requestJson<TraineeResponse[]>(`/api/mentor/trainees${q}`)
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

export async function assignCurriculum(traineeId: number, curriculumId: number): Promise<AssignmentResponse> {
  return requestJson<AssignmentResponse>(`/api/mentor/trainees/${traineeId}/assignments`, {
    method: 'POST',
    body: JSON.stringify({ curriculumId }),
  })
}

export async function listCurricula(): Promise<CurriculumResponse[]> {
  return requestJson<CurriculumResponse[]>('/api/mentor/curricula')
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
  body: { title: string; description?: string; sortOrder?: number; learningMaterialId?: number },
): Promise<TaskTemplateResponse> {
  return requestJson<TaskTemplateResponse>(`/api/mentor/curricula/${curriculumId}/task-templates`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateTaskTemplate(
  curriculumId: number,
  templateId: number,
  body: { title?: string; description?: string; sortOrder?: number; learningMaterialId?: number | null },
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
