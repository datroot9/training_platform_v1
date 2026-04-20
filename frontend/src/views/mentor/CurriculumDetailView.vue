<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Textarea from 'primevue/textarea'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type {
  CurriculumDetailResponse,
  CurriculumResponse,
  LearningMaterialResponse,
  TaskTemplateResponse,
} from '../../api/types'
import PageHeader from '../../components/layout/PageHeader.vue'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const curriculumId = computed(() => Number(route.params.id))
const activeTab = ref('overview')
const showFullHeaderDescription = ref(false)

const detail = ref<CurriculumDetailResponse | null>(null)
const siblingVersions = ref<CurriculumResponse[]>([])
const loading = ref(false)
const error = ref('')

const editName = ref('')
const editDescription = ref('')
const savingMeta = ref(false)

const uploadFile = ref<File | null>(null)
const uploadSortOrder = ref<number | null>(null)
const uploading = ref(false)
const materialDeleteDialogVisible = ref(false)
const materialToDelete = ref<LearningMaterialResponse | null>(null)

const templateDialogVisible = ref(false)
const templateDeleteDialogVisible = ref(false)
const templateMode = ref<'create' | 'edit'>('create')
const templateEditing = ref<TaskTemplateResponse | null>(null)
const templateToDelete = ref<TaskTemplateResponse | null>(null)
const templateTitle = ref('')
const templateDescription = ref('')
const templateSortOrder = ref<number | null>(null)
const templateMaterialId = ref<number | null>(null)
const templateSubmitting = ref(false)

const publishDialogVisible = ref(false)
const publishSubmitting = ref(false)

const newVersionDialogVisible = ref(false)
const newVersionLabel = ref('')
const newVersionName = ref('')
const newVersionDescription = ref('')
const newVersionSubmitting = ref(false)

const isPublished = computed(() => detail.value?.curriculum.status === 'PUBLISHED')
const hasMaterials = computed(() => (detail.value?.materials.length ?? 0) > 0)
const hasTemplates = computed(() => (detail.value?.taskTemplates.length ?? 0) > 0)
const publishReady = computed(() => hasMaterials.value && hasTemplates.value)

const materialOptions = computed(() =>
  (detail.value?.materials ?? []).map((item) => ({
    label: item.fileName,
    value: item.id,
  })),
)

const overviewDescription = computed(() => {
  const normalized = (detail.value?.curriculum.description ?? '').replace(/\.{3,}/g, '').trim()
  return normalized || 'No description yet.'
})

const headerDescriptionShort = computed(() => {
  if (overviewDescription.value.length <= 160) return overviewDescription.value
  return `${overviewDescription.value.slice(0, 160).trimEnd()}`
})
const canExpandHeaderDescription = computed(() => overviewDescription.value.length > 160)
const headerTagSeverity = computed(() => (isPublished.value ? 'success' : 'warn'))

const versionSelectOptions = computed(() =>
  siblingVersions.value.map((v) => ({
    label: `${v.versionLabel} · ${v.status}`,
    value: v.id,
  })),
)

const showVersionSwitcher = computed(() => versionSelectOptions.value.length > 1)

function formatDate(value?: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function mapMaterialName(materialId: number | null): string {
  if (!materialId) return 'Not linked'
  return detail.value?.materials.find((item) => item.id === materialId)?.fileName ?? `#${materialId}`
}

function parseUpdatedAt(iso: string): number {
  const t = new Date(iso).getTime()
  return Number.isNaN(t) ? 0 : t
}

async function loadSiblingVersions(groupId: number): Promise<void> {
  try {
    const all = await mentorApi.listAllCurricula({ sortBy: 'updatedAt', sortDir: 'desc', size: 100 })
    siblingVersions.value = all
      .filter((c) => c.curriculumGroupId === groupId)
      .sort((a, b) => parseUpdatedAt(b.updatedAt) - parseUpdatedAt(a.updatedAt))
  } catch {
    siblingVersions.value = []
  }
}

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const payload = await mentorApi.getCurriculum(curriculumId.value)
    detail.value = payload
    editName.value = payload.curriculum.name
    editDescription.value = payload.curriculum.description ?? ''
    await loadSiblingVersions(payload.curriculum.curriculumGroupId)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curriculum'
    detail.value = null
    siblingVersions.value = []
  } finally {
    loading.value = false
  }
}

function onPickVersion(nextId: number | null): void {
  if (nextId == null || nextId === curriculumId.value) return
  void router.push({ name: 'mentor-curriculum-detail', params: { id: String(nextId) } })
}

onMounted(() => {
  void load()
})

watch(curriculumId, () => {
  activeTab.value = 'overview'
  showFullHeaderDescription.value = false
  void load()
})

async function saveOverview(): Promise<void> {
  if (!detail.value || isPublished.value) return
  error.value = ''
  savingMeta.value = true
  try {
    await mentorApi.updateCurriculum(curriculumId.value, {
      name: editName.value.trim(),
      description: editDescription.value.trim(),
    })
    toast.add({
      severity: 'success',
      summary: 'Overview updated',
      detail: 'Curriculum metadata was saved.',
      life: 2500,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Update failed'
  } finally {
    savingMeta.value = false
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
}

async function uploadMaterial(): Promise<void> {
  if (!uploadFile.value || isPublished.value) return
  error.value = ''
  uploading.value = true
  try {
    await mentorApi.uploadMaterial(curriculumId.value, uploadFile.value, uploadSortOrder.value ?? undefined)
    uploadFile.value = null
    uploadSortOrder.value = null
    toast.add({
      severity: 'success',
      summary: 'Material uploaded',
      detail: 'PDF is now available in this curriculum.',
      life: 2500,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Upload failed'
  } finally {
    uploading.value = false
  }
}

function askDeleteMaterial(item: LearningMaterialResponse): void {
  materialToDelete.value = item
  materialDeleteDialogVisible.value = true
}

async function confirmDeleteMaterial(): Promise<void> {
  if (!materialToDelete.value || isPublished.value) return
  error.value = ''
  try {
    await mentorApi.deleteMaterial(curriculumId.value, materialToDelete.value.id)
    materialDeleteDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Material deleted',
      detail: `${materialToDelete.value.fileName} was removed.`,
      life: 2500,
    })
    materialToDelete.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Delete failed'
  }
}

function resetTemplateForm(): void {
  templateTitle.value = ''
  templateDescription.value = ''
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
  templateSortOrder.value = item.sortOrder
  templateMaterialId.value = item.learningMaterialId
  templateDialogVisible.value = true
}

async function submitTemplate(): Promise<void> {
  if (!templateTitle.value.trim() || isPublished.value) return
  error.value = ''
  templateSubmitting.value = true
  try {
    if (templateMode.value === 'create') {
      await mentorApi.createTaskTemplate(curriculumId.value, {
        title: templateTitle.value.trim(),
        description: templateDescription.value.trim() || undefined,
        sortOrder: templateSortOrder.value ?? undefined,
        learningMaterialId: templateMaterialId.value ?? undefined,
      })
      toast.add({
        severity: 'success',
        summary: 'Template created',
        detail: 'New task template has been added.',
        life: 2500,
      })
    } else if (templateEditing.value) {
      await mentorApi.updateTaskTemplate(curriculumId.value, templateEditing.value.id, {
        title: templateTitle.value.trim(),
        description: templateDescription.value.trim() || undefined,
        sortOrder: templateSortOrder.value ?? undefined,
        learningMaterialId: templateMaterialId.value,
      })
      toast.add({
        severity: 'success',
        summary: 'Template updated',
        detail: 'Task template changes were saved.',
        life: 2500,
      })
    }
    templateDialogVisible.value = false
    resetTemplateForm()
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Template save failed'
  } finally {
    templateSubmitting.value = false
  }
}

function askDeleteTemplate(item: TaskTemplateResponse): void {
  templateToDelete.value = item
  templateDeleteDialogVisible.value = true
}

async function confirmDeleteTemplate(): Promise<void> {
  if (!templateToDelete.value || isPublished.value) return
  error.value = ''
  try {
    await mentorApi.deleteTaskTemplate(curriculumId.value, templateToDelete.value.id)
    templateDeleteDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Template deleted',
      detail: `${templateToDelete.value.title} was removed.`,
      life: 2500,
    })
    templateToDelete.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Delete template failed'
  }
}

function askPublish(): void {
  if (!publishReady.value || isPublished.value) return
  publishDialogVisible.value = true
}

async function confirmPublish(): Promise<void> {
  if (!publishReady.value || isPublished.value) return
  error.value = ''
  publishSubmitting.value = true
  try {
    await mentorApi.publishCurriculum(curriculumId.value)
    publishDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Curriculum published',
      detail: 'Editing is now locked for this curriculum.',
      life: 3000,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publish failed'
  } finally {
    publishSubmitting.value = false
  }
}

function openNewVersionDialog(): void {
  if (!detail.value || !isPublished.value) return
  newVersionLabel.value = ''
  newVersionName.value = detail.value.curriculum.name
  newVersionDescription.value = detail.value.curriculum.description ?? ''
  newVersionDialogVisible.value = true
}

async function confirmCreateNewVersion(): Promise<void> {
  if (!detail.value || !isPublished.value) return
  const label = newVersionLabel.value.trim()
  if (!label) return
  error.value = ''
  newVersionSubmitting.value = true
  try {
    const created = await mentorApi.createCurriculumVersion(curriculumId.value, {
      versionLabel: label,
      name: newVersionName.value.trim() || undefined,
      description: newVersionDescription.value,
    })
    newVersionDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Draft version created',
      detail: `Opening version ${created.versionLabel}.`,
      life: 2800,
    })
    await router.push(`/mentor/curricula/${created.id}`)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not create new version'
  } finally {
    newVersionSubmitting.value = false
  }
}
</script>

<template>
  <div class="curriculum-detail-page">
    <Toast position="bottom-right" />

    <p v-if="loading">Loading...</p>
    <template v-else-if="detail">
      <PageHeader
        :title="detail.curriculum.name"
        :show-back="true"
        back-to="/mentor/curricula"
        :tag-value="detail.curriculum.status"
        :tag-severity="headerTagSeverity"
      >
        <template #actions>
          <div v-if="showVersionSwitcher" class="version-switch">
            <span class="version-switch-label">Version</span>
            <Select
              :model-value="curriculumId"
              :options="versionSelectOptions"
              option-label="label"
              option-value="value"
              class="version-select"
              @update:model-value="onPickVersion"
            />
          </div>
        </template>
        <template #description>
          <div class="header-description">
            <p>{{ showFullHeaderDescription ? overviewDescription : headerDescriptionShort }}</p>
            <Button
              v-if="canExpandHeaderDescription"
              :label="showFullHeaderDescription ? 'Show less' : 'Show more'"
              text
              size="small"
              @click="showFullHeaderDescription = !showFullHeaderDescription"
            />
          </div>
        </template>
      </PageHeader>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <Message v-if="isPublished" severity="info" :closable="false">
        This curriculum is published (version {{ detail.curriculum.versionLabel }}). Editing is locked; create a new
        draft version to change content.
        <span v-if="showVersionSwitcher">
          Use the version dropdown in the header to open another version in this family.
        </span>
      </Message>

      <Tabs v-model:value="activeTab">
        <TabList>
          <Tab value="overview">Overview</Tab>
          <Tab value="materials">Materials</Tab>
          <Tab value="templates">Task templates</Tab>
          <Tab value="publish">Publish</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="overview">
            <section class="card">
            <div class="form-grid">
              <label>
                Name
                <InputText v-model="editName" :disabled="isPublished" />
              </label>
              <label class="full">
                Description
                <Textarea v-model="editDescription" rows="4" auto-resize :disabled="isPublished" />
              </label>
            </div>
            <div class="actions">
              <Button
                label="Save overview"
                icon="pi pi-save"
                :loading="savingMeta"
                :disabled="isPublished || !editName.trim()"
                @click="saveOverview"
              />
            </div>
            </section>
          </TabPanel>

          <TabPanel value="materials">
            <section class="card">
            <div class="upload-row">
              <label>
                PDF file
                <input type="file" accept="application/pdf" :disabled="isPublished" @change="onFileChange" />
              </label>
              <label>
                Sort order (optional)
                <InputNumber v-model="uploadSortOrder" :min="0" :use-grouping="false" :disabled="isPublished" />
              </label>
              <Button
                label="Upload material"
                icon="pi pi-upload"
                :loading="uploading"
                :disabled="!uploadFile || isPublished"
                @click="uploadMaterial"
              />
            </div>

            <DataTable :value="detail.materials" data-key="id" responsive-layout="scroll" class="p-datatable-sm">
              <Column field="fileName" header="File name" style="min-width: 18rem" />
              <Column field="sortOrder" header="Sort order" style="width: 8rem" />
              <Column header="Uploaded" style="width: 12rem">
                <template #body="{ data }">
                  {{ formatDate(data.createdAt) }}
                </template>
              </Column>
              <Column header-style="width: 6rem">
                <template #body="{ data }">
                  <Button
                    icon="pi pi-trash"
                    text
                    severity="danger"
                    :disabled="isPublished"
                    @click="askDeleteMaterial(data)"
                  />
                </template>
              </Column>
            </DataTable>
            </section>
          </TabPanel>

          <TabPanel value="templates">
            <section class="card">
            <div class="toolbar-end">
              <Button
                label="Add template"
                icon="pi pi-plus"
                :disabled="isPublished"
                @click="openCreateTemplate"
              />
            </div>

            <DataTable :value="detail.taskTemplates" data-key="id" responsive-layout="scroll" class="p-datatable-sm">
              <Column field="title" header="Title" style="min-width: 14rem" />
              <Column field="sortOrder" header="Sort order" style="width: 8rem" />
              <Column header="Material link" style="min-width: 12rem">
                <template #body="{ data }">
                  {{ mapMaterialName(data.learningMaterialId) }}
                </template>
              </Column>
              <Column header-style="width: 8rem">
                <template #body="{ data }">
                  <div class="row-actions">
                    <Button icon="pi pi-pencil" text :disabled="isPublished" @click="openEditTemplate(data)" />
                    <Button
                      icon="pi pi-trash"
                      text
                      severity="danger"
                      :disabled="isPublished"
                      @click="askDeleteTemplate(data)"
                    />
                  </div>
                </template>
              </Column>
            </DataTable>
            </section>
          </TabPanel>

          <TabPanel value="publish">
            <section class="card">
            <h3>Publish checklist</h3>
            <ul class="checklist">
              <li>
                <i class="pi" :class="hasMaterials ? 'pi-check-circle ok' : 'pi-times-circle bad'" />
                At least one learning material uploaded
              </li>
              <li>
                <i class="pi" :class="hasTemplates ? 'pi-check-circle ok' : 'pi-times-circle bad'" />
                At least one task template created
              </li>
            </ul>
            <div class="actions">
              <Button
                label="Publish curriculum"
                icon="pi pi-send"
                severity="success"
                :disabled="isPublished || !publishReady"
                @click="askPublish"
              />
            </div>
            <template v-if="isPublished">
              <h3 class="versioning-heading">New version</h3>
              <p class="versioning-copy">
                Create a draft copy that shares the same PDF files on disk as this published version. Use a unique
                version label within this curriculum family (for example 1.1 or 2026-Q2).
              </p>
              <div class="actions">
                <Button
                  label="Create new draft version"
                  icon="pi pi-copy"
                  outlined
                  @click="openNewVersionDialog"
                />
              </div>
            </template>
            </section>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </template>
  </div>

  <Dialog
    v-model:visible="materialDeleteDialogVisible"
    modal
    header="Delete material"
    :style="{ width: '28rem' }"
  >
    <p>Delete <strong>{{ materialToDelete?.fileName }}</strong> from this curriculum?</p>
    <template #footer>
      <Button label="Cancel" text @click="materialDeleteDialogVisible = false" />
      <Button label="Delete" severity="danger" @click="confirmDeleteMaterial" />
    </template>
  </Dialog>

  <Dialog
    v-model:visible="templateDialogVisible"
    modal
    :header="templateMode === 'create' ? 'Add task template' : 'Edit task template'"
    :style="{ width: '34rem' }"
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
          placeholder="Select learning material"
        />
      </label>
    </div>
    <template #footer>
      <Button label="Cancel" text @click="templateDialogVisible = false" />
      <Button
        :label="templateMode === 'create' ? 'Create' : 'Save changes'"
        :loading="templateSubmitting"
        :disabled="!templateTitle.trim()"
        @click="submitTemplate"
      />
    </template>
  </Dialog>

  <Dialog
    v-model:visible="templateDeleteDialogVisible"
    modal
    header="Delete template"
    :style="{ width: '28rem' }"
  >
    <p>Delete <strong>{{ templateToDelete?.title }}</strong> from this curriculum?</p>
    <template #footer>
      <Button label="Cancel" text @click="templateDeleteDialogVisible = false" />
      <Button label="Delete" severity="danger" @click="confirmDeleteTemplate" />
    </template>
  </Dialog>

  <Dialog v-model:visible="publishDialogVisible" modal header="Publish curriculum" :style="{ width: '30rem' }">
    <p>
      Publish this curriculum now? After publishing, materials and templates are locked for editing.
    </p>
    <template #footer>
      <Button label="Cancel" text @click="publishDialogVisible = false" />
      <Button
        label="Publish"
        severity="success"
        :loading="publishSubmitting"
        :disabled="!publishReady"
        @click="confirmPublish"
      />
    </template>
  </Dialog>

  <Dialog
    v-model:visible="newVersionDialogVisible"
    modal
    header="Create new draft version"
    :style="{ width: '32rem' }"
  >
    <div class="dialog-form">
      <label>
        Version label (required)
        <InputText v-model="newVersionLabel" placeholder="e.g. 1.1 or 2026-Q2" />
      </label>
      <label>
        Name (optional)
        <InputText v-model="newVersionName" placeholder="Defaults to current curriculum name" />
      </label>
      <label>
        Description (optional)
        <Textarea v-model="newVersionDescription" rows="3" auto-resize />
      </label>
    </div>
    <template #footer>
      <Button label="Cancel" text @click="newVersionDialogVisible = false" />
      <Button
        label="Create draft"
        icon="pi pi-check"
        :loading="newVersionSubmitting"
        :disabled="!newVersionLabel.trim()"
        @click="confirmCreateNewVersion"
      />
    </template>
  </Dialog>
</template>

<style scoped>
.curriculum-detail-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.version-switch {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.version-switch-label {
  font-size: 0.9rem;
  color: var(--text-muted);
  white-space: nowrap;
}

.version-select {
  min-width: 12rem;
}

.header-description {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.1rem;
}

.header-description p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.card {
  background: #fff;
  border: 1px solid var(--brand-border-soft);
  border-radius: 12px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.8rem;
}

.form-grid label,
.upload-row label,
.dialog-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.form-grid .full {
  grid-column: 1 / -1;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.upload-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  align-items: flex-end;
}

.toolbar-end {
  display: flex;
  justify-content: flex-end;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
}

.checklist {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.checklist li {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.ok {
  color: #16a34a;
}

.bad {
  color: #dc2626;
}

.versioning-heading {
  margin: 1rem 0 0.35rem;
  font-size: 1rem;
}

.versioning-copy {
  margin: 0 0 0.5rem;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.45;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .actions,
  .toolbar-end {
    justify-content: flex-start;
  }
}
</style>
