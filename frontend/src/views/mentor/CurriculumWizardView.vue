<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import OrderList from 'primevue/orderlist'
import Select from 'primevue/select'
import Steps from 'primevue/steps'
import Textarea from 'primevue/textarea'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import { useCurriculumWizard } from '../../composables/useCurriculumWizard'
import { useMediaQuery } from '../../composables/useMediaQuery'
import type { LearningMaterialResponse, TaskTemplateResponse } from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

type PendingFile = { id: string; file: File }

const route = useRoute()
const router = useRouter()
const toast = useToast()

const {
  curriculumId,
  detail,
  loading,
  error,
  loadDetail,
  deriveInitialStep,
  createDraft,
  publish,
} = useCurriculumWizard()

const activeStep = ref(0)
const stepItems = [
  { label: 'Basics' },
  { label: 'Materials' },
  { label: 'Task templates' },
  { label: 'Review & publish' },
]

const metaName = ref('')
const metaDescription = ref('')
const basicsSubmitting = ref(false)

const pendingFiles = ref<PendingFile[]>([])
const uploading = ref(false)

const materialDeleteVisible = ref(false)
const materialToDelete = ref<LearningMaterialResponse | null>(null)

const templateDialogVisible = ref(false)
const templateDeleteVisible = ref(false)
const templateMode = ref<'create' | 'edit'>('create')
const templateEditing = ref<TaskTemplateResponse | null>(null)
const templateToDelete = ref<TaskTemplateResponse | null>(null)
const templateTitle = ref('')
const templateDescription = ref('')
const templateEstimatedDays = ref<number | null>(null)
const templateSortOrder = ref<number | null>(null)
const templateMaterialId = ref<number | null>(null)
const templateSubmitting = ref(false)

const publishSubmitting = ref(false)
const discardDialogVisible = ref(false)
const discardSubmitting = ref(false)
const bypassLeaveGuard = ref(false)
const isMobileTable = useMediaQuery('(max-width: 900px)')

const hasMaterials = computed(() => (detail.value?.materials.length ?? 0) > 0)
const hasTemplates = computed(() => (detail.value?.taskTemplates.length ?? 0) > 0)
const publishReady = computed(() => hasMaterials.value && hasTemplates.value)
const hasPersistedDraft = computed(
  () => curriculumId.value != null && detail.value?.curriculum.status === 'DRAFT',
)
const hasDraftProgress = computed(
  () =>
    !!metaName.value.trim() ||
    !!metaDescription.value.trim() ||
    pendingFiles.value.length > 0 ||
    hasMaterials.value ||
    hasTemplates.value,
)
const shouldWarnOnLeave = computed(
  () => hasPersistedDraft.value && hasDraftProgress.value && !bypassLeaveGuard.value,
)

const materialOptions = computed(() =>
  (detail.value?.materials ?? []).map((item) => ({
    label: item.fileName,
    value: item.id,
  })),
)

function mapMaterialName(materialId: number | null): string {
  if (!materialId) return 'Not linked'
  return detail.value?.materials.find((item) => item.id === materialId)?.fileName ?? `#${materialId}`
}

function pendingFileLabel(item: PendingFile): string {
  return item.file.name
}

function onAddPendingFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return
  for (let i = 0; i < files.length; i++) {
    const f = files[i]
    if (f) pendingFiles.value.push({ id: crypto.randomUUID(), file: f })
  }
  input.value = ''
}

function nextSortOrderBase(): number {
  const mats = detail.value?.materials ?? []
  if (mats.length === 0) return 0
  return Math.max(...mats.map((m) => m.sortOrder))
}

async function uploadPendingQueue(): Promise<void> {
  const id = curriculumId.value
  if (id == null || pendingFiles.value.length === 0) return
  uploading.value = true
  error.value = ''
  try {
    const base = nextSortOrderBase()
    for (let i = 0; i < pendingFiles.value.length; i++) {
      await mentorApi.uploadMaterial(id, pendingFiles.value[i].file, base + i + 1)
    }
    pendingFiles.value = []
    toast.add({
      severity: 'success',
      summary: 'Materials uploaded',
      detail: 'PDFs were added in list order.',
      life: 2500,
    })
    await loadDetail(id)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Upload failed'
  } finally {
    uploading.value = false
  }
}

function askDeleteMaterial(item: LearningMaterialResponse): void {
  materialToDelete.value = item
  materialDeleteVisible.value = true
}

async function confirmDeleteMaterial(): Promise<void> {
  const id = curriculumId.value
  if (id == null || !materialToDelete.value) return
  try {
    await mentorApi.deleteMaterial(id, materialToDelete.value.id)
    materialDeleteVisible.value = false
    materialToDelete.value = null
    toast.add({ severity: 'success', summary: 'Material removed', life: 2200 })
    await loadDetail(id)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Delete failed'
  }
}

function resetTemplateForm(): void {
  templateTitle.value = ''
  templateDescription.value = ''
  templateEstimatedDays.value = null
  templateSortOrder.value = null
  templateMaterialId.value = null
  templateEditing.value = null
}

function openCreateTemplate(): void {
  templateMode.value = 'create'
  resetTemplateForm()
  templateDialogVisible.value = true
}

function openEditTemplate(item: TaskTemplateResponse): void {
  templateMode.value = 'edit'
  templateEditing.value = item
  templateTitle.value = item.title
  templateDescription.value = item.description ?? ''
  templateEstimatedDays.value = item.estimatedDays
  templateSortOrder.value = item.sortOrder
  templateMaterialId.value = item.learningMaterialId
  templateDialogVisible.value = true
}

async function submitTemplate(): Promise<void> {
  const id = curriculumId.value
  if (id == null || !templateTitle.value.trim()) return
  templateSubmitting.value = true
  error.value = ''
  try {
    if (templateMode.value === 'create') {
      await mentorApi.createTaskTemplate(id, {
        title: templateTitle.value.trim(),
        description: templateDescription.value.trim() || undefined,
        estimatedDays: templateEstimatedDays.value ?? undefined,
        sortOrder: templateSortOrder.value ?? undefined,
        learningMaterialId: templateMaterialId.value ?? undefined,
      })
      toast.add({ severity: 'success', summary: 'Template added', life: 2200 })
    } else if (templateEditing.value) {
      await mentorApi.updateTaskTemplate(id, templateEditing.value.id, {
        title: templateTitle.value.trim(),
        description: templateDescription.value.trim() || undefined,
        estimatedDays: templateEstimatedDays.value ?? undefined,
        sortOrder: templateSortOrder.value ?? undefined,
        learningMaterialId: templateMaterialId.value,
      })
      toast.add({ severity: 'success', summary: 'Template updated', life: 2200 })
    }
    templateDialogVisible.value = false
    resetTemplateForm()
    await loadDetail(id)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Template save failed'
  } finally {
    templateSubmitting.value = false
  }
}

function askDeleteTemplate(item: TaskTemplateResponse): void {
  templateToDelete.value = item
  templateDeleteVisible.value = true
}

async function confirmDeleteTemplate(): Promise<void> {
  const id = curriculumId.value
  if (id == null || !templateToDelete.value) return
  try {
    await mentorApi.deleteTaskTemplate(id, templateToDelete.value.id)
    templateDeleteVisible.value = false
    templateToDelete.value = null
    toast.add({ severity: 'success', summary: 'Template removed', life: 2200 })
    await loadDetail(id)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Delete failed'
  }
}

async function continueFromBasics(): Promise<void> {
  if (!metaName.value.trim()) return
  basicsSubmitting.value = true
  error.value = ''
  try {
    if (curriculumId.value != null) {
      await mentorApi.updateCurriculum(curriculumId.value, {
        name: metaName.value.trim(),
        description: metaDescription.value.trim(),
      })
      await loadDetail(curriculumId.value)
      activeStep.value = 1
    } else {
      await createDraft(metaName.value, metaDescription.value)
      await router.replace({ query: { id: String(curriculumId.value) } })
      activeStep.value = 1
      toast.add({
        severity: 'success',
        summary: 'Draft created',
        detail: 'Add learning materials in the next step.',
        life: 2800,
      })
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not save basics'
  } finally {
    basicsSubmitting.value = false
  }
}

function goNext(): void {
  if (activeStep.value === 1 && !hasMaterials.value) {
    toast.add({ severity: 'warn', summary: 'Add at least one PDF', life: 2500 })
    return
  }
  if (activeStep.value === 2 && !hasTemplates.value) {
    toast.add({ severity: 'warn', summary: 'Add at least one task template', life: 2500 })
    return
  }
  if (activeStep.value < 3) {
    activeStep.value += 1
  }
}

function goBack(): void {
  if (activeStep.value > 0) {
    activeStep.value -= 1
  }
}

async function confirmPublish(): Promise<void> {
  const id = curriculumId.value
  if (id == null || !publishReady.value) return
  publishSubmitting.value = true
  error.value = ''
  try {
    await publish()
    toast.add({
      severity: 'success',
      summary: 'Curriculum published',
      detail: 'You can assign it to trainees from the Trainees page.',
      life: 4000,
    })
    bypassLeaveGuard.value = true
    await router.push({ name: 'mentor-curriculum-detail', params: { id: String(id) } })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publish failed'
  } finally {
    publishSubmitting.value = false
  }
}

function askDiscardDraft(): void {
  if (!hasPersistedDraft.value) return
  discardDialogVisible.value = true
}

async function confirmDiscardDraft(): Promise<void> {
  const id = curriculumId.value
  if (id == null || !hasPersistedDraft.value) return
  discardSubmitting.value = true
  error.value = ''
  try {
    await mentorApi.deleteCurriculumDraft(id)
    discardDialogVisible.value = false
    bypassLeaveGuard.value = true
    toast.add({
      severity: 'success',
      summary: 'Draft discarded',
      detail: 'Unpublished curriculum draft was deleted.',
      life: 2600,
    })
    await router.push({ name: 'mentor-curricula' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not discard draft'
  } finally {
    discardSubmitting.value = false
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (!shouldWarnOnLeave.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (!shouldWarnOnLeave.value) return true
  return window.confirm('You have an unpublished curriculum draft. Leave this page anyway?')
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  const qid = route.query.id
  if (qid == null || qid === '') {
    return
  }
  const id = Number(qid)
  if (Number.isNaN(id)) {
    return
  }
  await loadDetail(id)
  if (!detail.value) {
    return
  }
  if (detail.value.curriculum.status === 'PUBLISHED') {
    await router.replace({ name: 'mentor-curriculum-detail', params: { id: String(id) } })
    return
  }
  metaName.value = detail.value.curriculum.name
  metaDescription.value = detail.value.curriculum.description ?? ''
  activeStep.value = deriveInitialStep(detail.value)
})
</script>

<template>
  <div class="curriculum-wizard">
    <Toast position="bottom-right" />

    <PageHeader
      title="Create curriculum"
      description="Step-by-step: basics, PDFs, task templates, then publish."
      :show-back="true"
      back-to="/mentor/curricula"
    />

    <div v-if="hasPersistedDraft" class="wizard-actions">
      <Button
        label="Discard draft"
        icon="pi pi-trash"
        severity="danger"
        outlined
        :loading="discardSubmitting"
        @click="askDiscardDraft"
      />
    </div>

    <Message v-if="error" severity="error" :closable="false" class="wizard-error">{{ error }}</Message>

    <div v-if="loading && !detail" class="loading-state">Loading…</div>

    <template v-else>
      <Steps :model="stepItems" :active-step="activeStep" :readonly="true" class="wizard-steps" />

      <!-- Step 0: Basics -->
      <section v-show="activeStep === 0" class="card">
        <h2 class="step-title">Curriculum basics</h2>
        <div class="form-grid">
          <label>
            Name
            <InputText v-model="metaName" placeholder="e.g. Backend API fundamentals" />
          </label>
          <label class="full">
            Description
            <Textarea v-model="metaDescription" rows="4" auto-resize placeholder="Short summary for mentors" />
          </label>
        </div>
        <div class="step-actions">
          <Button
            label="Continue"
            icon="pi pi-arrow-right"
            icon-pos="right"
            :loading="basicsSubmitting"
            :disabled="!metaName.trim()"
            @click="continueFromBasics"
          />
        </div>
      </section>

      <!-- Step 1: Materials -->
      <section v-show="activeStep === 1" class="card">
        <h2 class="step-title">Learning materials (PDF)</h2>
        <p class="hint">
          Reorder files in the queue with the arrow buttons (top = first). Upload applies sort order 1, 2, 3…
        </p>

        <div class="queue-block">
          <label class="file-label">
            <span>Add PDFs to queue</span>
            <input
              type="file"
              accept="application/pdf"
              multiple
              class="file-input"
              @change="onAddPendingFiles"
            />
          </label>
          <OrderList v-model="pendingFiles" data-key="id" scroll-height="200px" class="order-list">
            <template #option="{ option }">
              <span class="pending-name">{{ pendingFileLabel(option as PendingFile) }}</span>
            </template>
          </OrderList>
          <Button
            label="Upload queue"
            icon="pi pi-upload"
            :disabled="pendingFiles.length === 0 || curriculumId == null"
            :loading="uploading"
            @click="uploadPendingQueue"
          />
        </div>


        <h3 class="sub-title">Uploaded</h3>
        <DataTable
          v-if="!isMobileTable"
          :value="detail?.materials ?? []"
          data-key="id"
          class="p-datatable-sm wizard-table"
          responsive-layout="scroll"
        >
          <Column field="sortOrder" header="#" style="width: 4rem" header-class="mobile-hidden-col" body-class="mobile-hidden-col" />
          <Column field="fileName" header="File" />
          <Column header-style="width: 5rem">
            <template #body="{ data }">
              <Button icon="pi pi-trash" text severity="danger" @click="askDeleteMaterial(data)" />
            </template>
          </Column>
        </DataTable>
        <ul v-else-if="(detail?.materials?.length ?? 0) > 0" class="mobile-card-list">
          <li v-for="item in detail?.materials ?? []" :key="item.id" class="mobile-card">
            <p class="mobile-card-title">{{ item.fileName }}</p>
            <p class="mobile-card-meta">Order: {{ item.sortOrder }}</p>
            <div class="mobile-card-actions">
              <Button icon="pi pi-trash" label="Delete" text severity="danger" @click="askDeleteMaterial(item)" />
            </div>
          </li>
        </ul>
        <p v-else class="muted">No uploaded materials yet.</p>

        <div class="step-actions spread">
          <Button label="Back" icon="pi pi-arrow-left" severity="secondary" outlined @click="goBack" />
          <Button label="Next" icon="pi pi-arrow-right" icon-pos="right" @click="goNext" />
        </div>
      </section>

      <!-- Step 2: Templates -->
      <section v-show="activeStep === 2" class="card">
        <h2 class="step-title">Task templates</h2>
        <div class="toolbar-end">
          <Button label="Add template" icon="pi pi-plus" @click="openCreateTemplate" />
        </div>
        <DataTable
          v-if="!isMobileTable"
          :value="detail?.taskTemplates ?? []"
          data-key="id"
          class="p-datatable-sm wizard-table"
          responsive-layout="scroll"
        >
          <Column field="title" header="Title" />
          <Column header="Estimate (days)" style="width: 9rem">
            <template #body="{ data }">
              {{ data.estimatedDays ?? '-' }}
            </template>
          </Column>
          <Column field="sortOrder" header="Order" style="width: 6rem" header-class="mobile-hidden-col" body-class="mobile-hidden-col" />
          <Column header="Material" header-class="mobile-hidden-col" body-class="mobile-hidden-col">
            <template #body="{ data }">
              {{ mapMaterialName(data.learningMaterialId) }}
            </template>
          </Column>
          <Column header-style="width: 8rem">
            <template #body="{ data }">
              <Button icon="pi pi-pencil" text @click="openEditTemplate(data)" />
              <Button icon="pi pi-trash" text severity="danger" @click="askDeleteTemplate(data)" />
            </template>
          </Column>
        </DataTable>
        <ul v-else-if="(detail?.taskTemplates?.length ?? 0) > 0" class="mobile-card-list">
          <li v-for="item in detail?.taskTemplates ?? []" :key="item.id" class="mobile-card">
            <p class="mobile-card-title">{{ item.title }}</p>
            <p class="mobile-card-meta">Estimate: {{ item.estimatedDays ?? '-' }} day(s)</p>
            <p class="mobile-card-meta">Order: {{ item.sortOrder }}</p>
            <p class="mobile-card-meta">Material: {{ mapMaterialName(item.learningMaterialId) }}</p>
            <div class="mobile-card-actions">
              <Button icon="pi pi-pencil" label="Edit" text @click="openEditTemplate(item)" />
              <Button icon="pi pi-trash" label="Delete" text severity="danger" @click="askDeleteTemplate(item)" />
            </div>
          </li>
        </ul>
        <p v-else class="muted">No task templates yet.</p>

        <div class="step-actions spread">
          <Button label="Back" icon="pi pi-arrow-left" severity="secondary" outlined @click="goBack" />
          <Button label="Next" icon="pi pi-arrow-right" icon-pos="right" @click="goNext" />
        </div>
      </section>

      <!-- Step 3: Review -->
      <section v-show="activeStep === 3" class="card">
        <h2 class="step-title">Review & publish</h2>
        <ul class="checklist">
          <li>
            <i class="pi" :class="hasMaterials ? 'pi-check-circle ok' : 'pi-times-circle bad'" />
            At least one learning material
          </li>
          <li>
            <i class="pi" :class="hasTemplates ? 'pi-check-circle ok' : 'pi-times-circle bad'" />
            At least one task template
          </li>
        </ul>
        <p v-if="detail" class="review-summary">
          <strong>{{ detail.curriculum.name }}</strong>
          · version {{ detail.curriculum.versionLabel }}
          · {{ detail.materials.length }} PDF(s), {{ detail.taskTemplates.length }} template(s)
        </p>
        <div class="step-actions spread">
          <Button label="Back" icon="pi pi-arrow-left" severity="secondary" outlined @click="goBack" />
          <Button
            label="Publish curriculum"
            icon="pi pi-send"
            severity="success"
            :loading="publishSubmitting"
            :disabled="!publishReady"
            @click="confirmPublish"
          />
        </div>
      </section>
    </template>

    <Dialog
      v-model:visible="materialDeleteVisible"
      modal
      header="Remove material"
      :style="{ width: 'min(26rem, 92vw)' }"
    >
      <p>Remove <strong>{{ materialToDelete?.fileName }}</strong> from this draft?</p>
      <template #footer>
        <Button label="Cancel" text @click="materialDeleteVisible = false" />
        <Button label="Remove" severity="danger" @click="confirmDeleteMaterial" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="templateDialogVisible"
      modal
      :header="templateMode === 'create' ? 'Add task template' : 'Edit task template'"
      :style="{ width: 'min(34rem, 92vw)' }"
    >
      <div class="dialog-form">
        <label>
          Title
          <InputText v-model="templateTitle" />
        </label>
        <label>
          Description
          <Textarea v-model="templateDescription" rows="3" auto-resize />
        </label>
        <label>
          Estimated days (optional)
          <InputNumber v-model="templateEstimatedDays" :min="1" :max="3650" :use-grouping="false" />
        </label>
        <label>
          Sort order (optional)
          <InputNumber v-model="templateSortOrder" :min="0" :use-grouping="false" />
        </label>
        <label>
          Linked material (optional)
          <Select
            v-model="templateMaterialId"
            :options="materialOptions"
            option-label="label"
            option-value="value"
            show-clear
            placeholder="Select PDF"
          />
        </label>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="templateDialogVisible = false" />
        <Button
          :label="templateMode === 'create' ? 'Add' : 'Save'"
          :loading="templateSubmitting"
          :disabled="!templateTitle.trim()"
          @click="submitTemplate"
        />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="templateDeleteVisible"
      modal
      header="Remove template"
      :style="{ width: 'min(26rem, 92vw)' }"
    >
      <p>Remove <strong>{{ templateToDelete?.title }}</strong>?</p>
      <template #footer>
        <Button label="Cancel" text @click="templateDeleteVisible = false" />
        <Button label="Remove" severity="danger" @click="confirmDeleteTemplate" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="discardDialogVisible"
      modal
      header="Discard draft"
      :style="{ width: 'min(28rem, 92vw)' }"
    >
      <p>
        This will permanently delete this unpublished draft, including uploaded materials and task templates. Continue?
      </p>
      <template #footer>
        <Button label="Cancel" text @click="discardDialogVisible = false" />
        <Button
          label="Discard"
          icon="pi pi-trash"
          severity="danger"
          :loading="discardSubmitting"
          @click="confirmDiscardDraft"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.curriculum-wizard {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.wizard-actions {
  display: flex;
  justify-content: flex-end;
}

.wizard-error {
  margin: 0;
}

.loading-state {
  padding: 1rem;
  color: var(--text-muted);
}

.wizard-steps {
  margin-bottom: 0.25rem;
}

.card {
  background: var(--ui-surface);
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-md);
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  box-shadow: var(--ui-shadow-sm);
}

.step-title {
  margin: 0;
  font-size: 1.1rem;
}

.sub-title {
  margin: 0.75rem 0 0.35rem;
  font-size: 0.95rem;
}

.hint {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-muted);
  line-height: 1.45;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.8rem;
}

.form-grid label,
.file-label,
.dialog-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.form-grid .full {
  grid-column: 1 / -1;
}

.file-input {
  font-size: 0.85rem;
}

.queue-block {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  align-items: flex-start;
}

.order-list {
  width: 100%;
  max-width: 36rem;
}

.pending-name {
  word-break: break-all;
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.5rem;
}

.step-actions.spread {
  justify-content: space-between;
}

.toolbar-end {
  display: flex;
  justify-content: flex-end;
}

.checklist {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.ok {
  color: #16a34a;
}

.bad {
  color: #dc2626;
}

.review-summary {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.mobile-card-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.mobile-card {
  border: 1px solid var(--ui-border-soft);
  border-radius: 10px;
  background: var(--ui-surface);
  padding: 0.65rem;
}

.mobile-card-title {
  margin: 0;
  font-weight: 600;
  color: var(--ui-text-primary);
}

.mobile-card-meta {
  margin: 0.35rem 0 0;
  font-size: 0.84rem;
  color: var(--ui-text-secondary);
}

.mobile-card-actions {
  margin-top: 0.55rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

@media (max-width: 900px) {
  :deep(.wizard-table .mobile-hidden-col) {
    display: none;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
