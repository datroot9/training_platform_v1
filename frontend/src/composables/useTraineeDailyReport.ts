import { computed, ref, watch, type Ref } from 'vue'
import { ApiError } from '../api/client'
import * as traineeApi from '../api/modules/trainee'
import type {
  DailyReportResourceInput,
  DailyReportResponse,
  SaveDailyReportRequest,
  WeeklySummaryResponse,
} from '../api/types'

function isoDate(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function startOfWeek(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const shift = day === 0 ? 6 : day - 1
  d.setDate(d.getDate() - shift)
  d.setHours(0, 0, 0, 0)
  return d
}

export function useTraineeDailyReport(
  assignmentId: Ref<number | null>,
  defaultDisplayName: Ref<string>,
) {
  const loading = ref(false)
  const submitting = ref(false)
  const error = ref('')
  const weekStart = ref<Date>(startOfWeek(new Date()))
  const selectedDate = ref<Date>(new Date())
  const weekReports = ref<DailyReportResponse[]>([])
  const weeklySummaries = ref<WeeklySummaryResponse[]>([])

  const fresherLabel = ref('')
  const trainingDayIndex = ref<number | null>(null)
  const whatDone = ref('')
  const plannedTomorrow = ref('')
  const blockers = ref('N/A')
  const resources = ref<DailyReportResourceInput[]>([])
  const taskHours = ref<Record<number, { hours: number | null; notes: string }>>({})
  const reportStatus = ref<'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REVISION_REQUIRED' | null>(null)

  const weekStartIso = computed(() => isoDate(weekStart.value))
  const selectedDateIso = computed(() => isoDate(selectedDate.value))

  const isWeekLocked = computed(() =>
    weeklySummaries.value.some((item) => {
      if (!item.finalizedAt) return false
      const reportDate = selectedDateIso.value
      return reportDate >= item.weekStart && reportDate <= item.weekEnd
    }),
  )

  const canEdit = computed(() => !isWeekLocked.value)

  function defaultResources(): DailyReportResourceInput[] {
    return [
      { type: 'TRELLO', label: 'Trello', url: '' },
      { type: 'GITHUB', label: 'Github', url: '' },
      { type: 'DRIVE', label: 'Drive', url: '' },
    ]
  }

  function resetForm(): void {
    fresherLabel.value = defaultDisplayName.value || 'Fresher'
    trainingDayIndex.value = null
    whatDone.value = ''
    plannedTomorrow.value = ''
    blockers.value = 'N/A'
    resources.value = defaultResources()
    taskHours.value = {}
    reportStatus.value = null
  }

  function fillFromReport(report: DailyReportResponse): void {
    fresherLabel.value = report.fresherLabel
    trainingDayIndex.value = report.trainingDayIndex
    whatDone.value = report.whatDone
    plannedTomorrow.value = report.plannedTomorrow
    blockers.value = report.blockers
    resources.value =
      report.resources.length > 0
        ? report.resources.map((item) => ({
            type: item.type,
            label: item.label,
            url: item.url,
          }))
        : defaultResources()
    const hours: Record<number, { hours: number | null; notes: string }> = {}
    for (const item of report.taskHours) {
      hours[item.taskId] = { hours: item.hours, notes: item.notes ?? '' }
    }
    taskHours.value = hours
    reportStatus.value = report.status
  }

  async function loadWeek(): Promise<void> {
    const aid = assignmentId.value
    if (aid == null) {
      weekReports.value = []
      weeklySummaries.value = []
      resetForm()
      return
    }
    loading.value = true
    error.value = ''
    try {
      const [reports, summaries] = await Promise.all([
        traineeApi.getDailyReportsByWeek(aid, weekStartIso.value),
        traineeApi.getWeeklySummaries(aid),
      ])
      weekReports.value = reports
      weeklySummaries.value = summaries
      const existing = reports.find((item) => item.reportDate === selectedDateIso.value)
      if (existing) {
        fillFromReport(existing)
      } else {
        resetForm()
      }
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Could not load daily report data'
    } finally {
      loading.value = false
    }
  }

  function buildPayload(): SaveDailyReportRequest {
    return {
      fresherLabel: fresherLabel.value.trim(),
      trainingDayIndex: Number(trainingDayIndex.value ?? 1),
      whatDone: whatDone.value.trim(),
      plannedTomorrow: plannedTomorrow.value.trim(),
      blockers: blockers.value.trim(),
      resources: resources.value
        .map((item) => ({
          type: item.type.trim(),
          label: item.label?.trim() || null,
          url: item.url.trim(),
        }))
        .filter((item) => item.type && item.url),
      taskHours: [],
    }
  }

  async function saveDraft(): Promise<void> {
    const aid = assignmentId.value
    if (aid == null || !canEdit.value) return
    submitting.value = true
    error.value = ''
    try {
      const saved = await traineeApi.saveDailyReportDraft(aid, selectedDateIso.value, buildPayload())
      reportStatus.value = saved.status
      await loadWeek()
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Could not save draft'
      throw e
    } finally {
      submitting.value = false
    }
  }

  async function submit(): Promise<void> {
    const aid = assignmentId.value
    if (aid == null || !canEdit.value) return
    submitting.value = true
    error.value = ''
    try {
      const saved = await traineeApi.submitDailyReport(aid, selectedDateIso.value, buildPayload())
      reportStatus.value = saved.status
      await loadWeek()
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Could not submit report'
      throw e
    } finally {
      submitting.value = false
    }
  }

  watch([selectedDate, assignmentId], () => {
    if (assignmentId.value == null) return
    const report = weekReports.value.find((item) => item.reportDate === selectedDateIso.value)
    if (report) {
      fillFromReport(report)
    } else {
      resetForm()
    }
  })

  watch(defaultDisplayName, (value) => {
    if (!fresherLabel.value.trim() || fresherLabel.value === 'Fresher') {
      fresherLabel.value = value || 'Fresher'
    }
  })

  return {
    loading,
    submitting,
    error,
    weekStart,
    selectedDate,
    weekReports,
    weeklySummaries,
    reportStatus,
    canEdit,
    fresherLabel,
    trainingDayIndex,
    whatDone,
    plannedTomorrow,
    blockers,
    resources,
    taskHours,
    loadWeek,
    saveDraft,
    submit,
  }
}
