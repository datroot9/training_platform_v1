import { ref } from 'vue'
import { ApiError } from '../api/client'
import * as mentorApi from '../api/modules/mentor'
import type { CurriculumDetailResponse, CurriculumResponse } from '../api/types'

export function useCurriculumWizard() {
  const curriculumId = ref<number | null>(null)
  const detail = ref<CurriculumDetailResponse | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function loadDetail(id: number): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      detail.value = await mentorApi.getCurriculum(id)
      curriculumId.value = id
    } catch (e) {
      error.value = e instanceof ApiError ? e.message : 'Failed to load curriculum'
      detail.value = null
      curriculumId.value = null
    } finally {
      loading.value = false
    }
  }

  function deriveInitialStep(d: CurriculumDetailResponse): number {
    if (d.curriculum.status !== 'DRAFT') {
      return 0
    }
    if (d.materials.length === 0) {
      return 1
    }
    if (d.taskTemplates.length === 0) {
      return 2
    }
    return 3
  }

  async function createDraft(name: string, description: string): Promise<CurriculumResponse> {
    const c = await mentorApi.createCurriculum({
      name: name.trim(),
      description: description.trim(),
    })
    curriculumId.value = c.id
    await loadDetail(c.id)
    return c
  }

  async function publish(): Promise<CurriculumResponse> {
    const id = curriculumId.value
    if (id == null) {
      throw new Error('No curriculum id')
    }
    return mentorApi.publishCurriculum(id)
  }

  return {
    curriculumId,
    detail,
    loading,
    error,
    loadDetail,
    deriveInitialStep,
    createDraft,
    publish,
  }
}
