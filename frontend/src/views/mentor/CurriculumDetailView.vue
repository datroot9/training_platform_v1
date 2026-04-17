<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type { CurriculumDetailResponse } from '../../api/types'

const route = useRoute()
const curriculumId = computed(() => Number(route.params.id))

const detail = ref<CurriculumDetailResponse | null>(null)
const loading = ref(false)
const error = ref('')

const editName = ref('')
const editDescription = ref('')

const tmplTitle = ref('')
const tmplDescription = ref('')
const tmplMaterialId = ref<number | null>(null)

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const d = await mentorApi.getCurriculum(curriculumId.value)
    detail.value = d
    editName.value = d.curriculum.name
    editDescription.value = d.curriculum.description ?? ''
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curriculum'
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(curriculumId, () => {
  void load()
})

async function saveMeta(): Promise<void> {
  error.value = ''
  try {
    await mentorApi.updateCurriculum(curriculumId.value, {
      name: editName.value,
      description: editDescription.value,
    })
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Update failed'
  }
}

const uploadFile = ref<File | null>(null)

function onFileChange(ev: Event): void {
  const input = ev.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
}

async function upload(): Promise<void> {
  if (!uploadFile.value) return
  error.value = ''
  try {
    await mentorApi.uploadMaterial(curriculumId.value, uploadFile.value)
    uploadFile.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Upload failed'
  }
}

async function removeMaterial(materialId: number): Promise<void> {
  if (!window.confirm('Delete this material?')) return
  error.value = ''
  try {
    await mentorApi.deleteMaterial(curriculumId.value, materialId)
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Delete failed'
  }
}

async function addTemplate(): Promise<void> {
  error.value = ''
  try {
    await mentorApi.createTaskTemplate(curriculumId.value, {
      title: tmplTitle.value,
      description: tmplDescription.value || undefined,
      learningMaterialId: tmplMaterialId.value ?? undefined,
    })
    tmplTitle.value = ''
    tmplDescription.value = ''
    tmplMaterialId.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Create template failed'
  }
}

async function publish(): Promise<void> {
  error.value = ''
  try {
    await mentorApi.publishCurriculum(curriculumId.value)
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Publish failed'
  }
}
</script>

<template>
  <div>
    <p v-if="loading">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="detail">
      <header class="head">
        <h1>{{ detail.curriculum.name }}</h1>
        <span class="pill">{{ detail.curriculum.status }}</span>
      </header>

      <section class="card">
        <h2>Details</h2>
        <label>
          Name
          <input v-model="editName" />
        </label>
        <label>
          Description
          <textarea v-model="editDescription" rows="3" />
        </label>
        <button type="button" @click="saveMeta">Save</button>
        <button
          v-if="detail.curriculum.status === 'DRAFT'"
          type="button"
          class="primary"
          @click="publish"
        >
          Publish
        </button>
      </section>

      <section class="card">
        <h2>Materials (PDF)</h2>
        <div class="row">
          <input type="file" accept="application/pdf" @change="onFileChange" />
          <button type="button" :disabled="!uploadFile" @click="upload">Upload</button>
        </div>
        <ul>
          <li v-for="m in detail.materials" :key="m.id">
            #{{ m.id }} {{ m.fileName }}
            <button type="button" @click="removeMaterial(m.id)">Delete</button>
          </li>
        </ul>
      </section>

      <section class="card">
        <h2>Task templates</h2>
        <div class="row">
          <label>
            Title
            <input v-model="tmplTitle" />
          </label>
          <label>
            Description
            <input v-model="tmplDescription" />
          </label>
          <label>
            Material ID (optional)
            <input v-model.number="tmplMaterialId" type="number" />
          </label>
          <button type="button" @click="addTemplate">Add</button>
        </div>
        <ul>
          <li v-for="t in detail.taskTemplates" :key="t.id">
            #{{ t.id }} {{ t.title }}
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.pill {
  font-size: 0.75rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #e2e8f0;
}
.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
input,
textarea {
  padding: 0.45rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}
button {
  align-self: flex-start;
  padding: 0.45rem 0.65rem;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  background: #fff;
  cursor: pointer;
}
button.primary {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
}
.error {
  color: #b91c1c;
}
ul {
  padding-left: 1.2rem;
}
</style>
