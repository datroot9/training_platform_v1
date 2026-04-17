<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ApiError } from '../../api/client'
import * as traineeApi from '../../api/modules/trainee'
import type { AssignmentResponse, AssignmentTaskResponse } from '../../api/types'

const assignment = ref<AssignmentResponse | null>(null)
const tasks = ref<AssignmentTaskResponse[]>([])
const loading = ref(false)
const error = ref('')

const materialId = ref<number | null>(null)

async function loadAssignment(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    assignment.value = await traineeApi.getActiveAssignment()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not load assignment'
    assignment.value = null
    tasks.value = []
  } finally {
    loading.value = false
  }
}

async function loadTasks(): Promise<void> {
  if (!assignment.value) {
    tasks.value = []
    return
  }
  try {
    tasks.value = await traineeApi.getAssignmentTasks(assignment.value.id)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not load tasks'
    tasks.value = []
  }
}

onMounted(async () => {
  await loadAssignment()
  await loadTasks()
})

watch(assignment, async () => {
  await loadTasks()
})

async function download(): Promise<void> {
  if (materialId.value == null) return
  error.value = ''
  try {
    const { blob, fileName } = await traineeApi.downloadMaterial(materialId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Download failed'
  }
}
</script>

<template>
  <div>
    <h1>My assignment</h1>
    <p class="muted">Tasks generated for your active curriculum assignment.</p>

    <p v-if="loading">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <section v-else-if="assignment" class="card">
      <p><strong>Curriculum:</strong> {{ assignment.curriculumName }}</p>
      <p><strong>Status:</strong> {{ assignment.status }}</p>
      <p><strong>Tasks generated:</strong> {{ assignment.generatedTaskCount }}</p>
    </section>
    <p v-else class="muted">No active assignment.</p>

    <section v-if="assignment" class="card">
      <h2>Tasks</h2>
      <table class="table">
        <thead>
          <tr>
            <th>Order</th>
            <th>Title</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td>{{ t.sortOrder }}</td>
            <td>{{ t.title }}</td>
            <td>{{ t.status }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>Download material (by ID)</h2>
      <p class="hint">
        Use a material ID from your mentor’s curriculum (shown in mentor UI). Backend does not expose a trainee “list materials” endpoint yet.
      </p>
      <div class="row">
        <input v-model.number="materialId" type="number" placeholder="material id" />
        <button type="button" :disabled="materialId == null" @click="download">Download PDF</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.muted {
  color: #64748b;
}
.hint {
  font-size: 0.85rem;
  color: #64748b;
}
.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e2e8f0;
  padding: 0.45rem 0.5rem;
  text-align: left;
  font-size: 0.9rem;
}
.row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
input {
  padding: 0.45rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}
button {
  padding: 0.45rem 0.65rem;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  background: #fff;
  cursor: pointer;
}
.error {
  color: #b91c1c;
}
</style>
