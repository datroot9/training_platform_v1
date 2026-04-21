/** Mirrors backend `ApiResponse<T>` */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export interface PagedResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Role = 'MENTOR' | 'TRAINEE'
export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE'

export interface StoredUser {
  userId: number
  email: string
  role: Role
}

export interface AuthResponse {
  userId: number
  email: string
  role: string
  mustChangePassword: boolean
  tokenType: string
  expiresInSeconds: number
  accessToken: string
  refreshToken: string
}

export interface TraineeResponse {
  id: number
  email: string
  fullName: string
  active: boolean
  mentorId: number
  createdAt: string
  /** Present when list API includes active assignment summary */
  activeAssignmentId?: number | null
  activeCurriculumName?: string | null
  completedTaskCount?: number | null
  totalTaskCount?: number | null
}

export interface CreateTraineeResponse {
  userId: number
  email: string
  fullName: string
  role: string
  temporaryPassword: string
  mustChangePassword: boolean
}

export interface CurriculumResponse {
  id: number
  curriculumGroupId: number
  versionLabel: string
  name: string
  description: string
  status: string
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CurriculumDetailResponse {
  curriculum: CurriculumResponse
  materials: LearningMaterialResponse[]
  taskTemplates: TaskTemplateResponse[]
}

export interface LearningMaterialResponse {
  id: number
  curriculumId: number
  fileName: string
  sortOrder: number
  createdAt: string
}

export interface TaskTemplateResponse {
  id: number
  curriculumId: number
  title: string
  description: string | null
  estimatedDays: number | null
  sortOrder: number
  learningMaterialId: number | null
  createdAt: string
  updatedAt: string
}

export interface AssignmentResponse {
  id: number
  traineeId: number
  curriculumId: number
  curriculumName: string
  curriculumDescription: string | null
  curriculumVersionLabel: string | null
  mentorName: string | null
  mentorEmail: string | null
  totalEstimatedDays: number | null
  status: string
  assignedAt: string
  endedAt: string | null
  generatedTaskCount: number
}

export interface AssignmentTaskResponse {
  id: number
  assignmentId: number
  taskTemplateId: number
  sortOrder: number
  title: string
  description: string | null
  estimatedDays: number | null
  status: TaskStatus
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
  learningMaterialId: number | null
  learningMaterialFileName: string | null
}

export type DailyReportStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REVISION_REQUIRED'
export type WeeklyReviewStatus = 'PENDING' | 'REVIEWED'

export interface DailyReportTaskHourInput {
  taskId: number
  hours: number
  notes: string | null
}

export interface DailyReportResourceInput {
  type: string
  label: string | null
  url: string
}

export interface SaveDailyReportRequest {
  fresherLabel: string
  trainingDayIndex: number
  whatDone: string
  plannedTomorrow: string
  blockers: string
  resources: DailyReportResourceInput[]
  taskHours: DailyReportTaskHourInput[]
}

export interface DailyReportTaskHourResponse {
  id: number
  taskId: number
  hours: number
  notes: string | null
}

export interface DailyReportResourceResponse {
  id: number
  type: string
  label: string | null
  url: string
}

export interface DailyReportResponse {
  id: number
  assignmentId: number
  reportDate: string
  status: DailyReportStatus
  fresherLabel: string
  trainingDayIndex: number
  whatDone: string
  plannedTomorrow: string
  blockers: string
  resources: DailyReportResourceResponse[]
  submittedAt: string | null
  reviewedAt: string | null
  taskHours: DailyReportTaskHourResponse[]
}

export interface WeeklySummaryResponse {
  id: number
  assignmentId: number
  weekStart: string
  weekEnd: string
  summaryText: string | null
  completionRate: number | null
  averageDailyHours: number | null
  reviewStatus: WeeklyReviewStatus
  mentorFeedback: string | null
  mentorGrade: number | null
  reviewedAt: string | null
  finalizedAt: string | null
  generatedAt: string
}

export interface ReviewWeeklySummaryRequest {
  mentorGrade: number
  mentorFeedback: string
  finalizeWeek?: boolean
}

export interface WeeklySummaryGenerationPlaceholderResponse {
  status: string
  message: string
}
