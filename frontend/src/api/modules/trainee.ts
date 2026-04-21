import { ApiError, requestJson, requestRaw } from '../client'
import type {
  AssignmentResponse,
  AssignmentTaskResponse,
  DailyReportResponse,
  SaveDailyReportRequest,
  TaskStatus,
  WeeklySummaryResponse,
} from '../types'

export async function getActiveAssignment(): Promise<AssignmentResponse> {
  return requestJson<AssignmentResponse>('/api/trainee/assignments/active')
}

/** Returns null when the trainee has no active assignment (HTTP 404 from API). */
export async function getActiveAssignmentOrNull(): Promise<AssignmentResponse | null> {
  try {
    return await getActiveAssignment()
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 404) return null
    throw e
  }
}

export async function getAssignmentTasks(assignmentId: number): Promise<AssignmentTaskResponse[]> {
  return requestJson<AssignmentTaskResponse[]>(`/api/trainee/assignments/${assignmentId}/tasks`)
}

export async function updateTaskStatus(
  assignmentId: number,
  taskId: number,
  status: TaskStatus,
): Promise<AssignmentTaskResponse> {
  return requestJson<AssignmentTaskResponse>(`/api/trainee/assignments/${assignmentId}/tasks/${taskId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

function parseFilename(contentDisposition: string | null): string {
  if (!contentDisposition) return 'download.pdf'
  const m = /filename\*?=(?:UTF-8''|")?([^\";]+)/i.exec(contentDisposition)
  if (m?.[1]) {
    try {
      return decodeURIComponent(m[1].replace(/"/g, ''))
    } catch {
      return m[1]
    }
  }
  return 'download.pdf'
}

export async function downloadMaterial(materialId: number): Promise<{ blob: Blob; fileName: string }> {
  const res = await requestRaw(`/api/trainee/materials/${materialId}/download`, { method: 'GET' })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || res.statusText)
  }
  const fileName = parseFilename(res.headers.get('Content-Disposition'))
  const blob = await res.blob()
  return { blob, fileName }
}

function toIsoDate(date: string | Date): string {
  if (typeof date === 'string') return date
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export async function getDailyReportsByWeek(
  assignmentId: number,
  weekStart: string | Date,
): Promise<DailyReportResponse[]> {
  return requestJson<DailyReportResponse[]>(
    `/api/trainee/assignments/${assignmentId}/daily-reports?weekStart=${encodeURIComponent(toIsoDate(weekStart))}`,
  )
}

export async function getDailyReportByDate(
  assignmentId: number,
  reportDate: string | Date,
): Promise<DailyReportResponse> {
  return requestJson<DailyReportResponse>(
    `/api/trainee/assignments/${assignmentId}/daily-reports/${encodeURIComponent(toIsoDate(reportDate))}`,
  )
}

export async function getDailyReportByDateOrNull(
  assignmentId: number,
  reportDate: string | Date,
): Promise<DailyReportResponse | null> {
  try {
    return await getDailyReportByDate(assignmentId, reportDate)
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 404) return null
    throw e
  }
}

export async function saveDailyReportDraft(
  assignmentId: number,
  reportDate: string | Date,
  body: SaveDailyReportRequest,
): Promise<DailyReportResponse> {
  return requestJson<DailyReportResponse>(
    `/api/trainee/assignments/${assignmentId}/daily-reports/${encodeURIComponent(toIsoDate(reportDate))}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    },
  )
}

export async function submitDailyReport(
  assignmentId: number,
  reportDate: string | Date,
  body: SaveDailyReportRequest,
): Promise<DailyReportResponse> {
  return requestJson<DailyReportResponse>(
    `/api/trainee/assignments/${assignmentId}/daily-reports/${encodeURIComponent(toIsoDate(reportDate))}/submit`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  )
}

export async function getWeeklySummaries(assignmentId: number): Promise<WeeklySummaryResponse[]> {
  return requestJson<WeeklySummaryResponse[]>(`/api/trainee/assignments/${assignmentId}/weekly-summaries`)
}
