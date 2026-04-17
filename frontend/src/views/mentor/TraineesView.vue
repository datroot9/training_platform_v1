<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type { CurriculumResponse, TraineeResponse } from '../../api/types'

const query = ref('')
const rows = ref<TraineeResponse[]>([])
const curricula = ref<CurriculumResponse[]>([])
const loading = ref(false)
const error = ref('')

const createEmail = ref('')
const createName = ref('')
const createMsg = ref('')

const assignTraineeId = ref<number | null>(null)
const assignCurriculumId = ref<number | null>(null)

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    rows.value = await mentorApi.listTrainees(query.value)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load trainees'
  } finally {
    loading.value = false
  }
}

async function loadCurricula(): Promise<void> {
  try {
    curricula.value = await mentorApi.listCurricula()
  } catch {
    /* optional for assign dropdown */
  }
}

onMounted(() => {
  void load()
  void loadCurricula()
})

async function createTrainee(): Promise<void> {
  createMsg.value = ''
  error.value = ''
  try {
    const res = await mentorApi.createTrainee({ email: createEmail.value, fullName: createName.value })
    createMsg.value = `Created. Temporary password: ${res.temporaryPassword}`
    createEmail.value = ''
    createName.value = ''
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Create failed'
  }
}

async function toggleActive(row: TraineeResponse): Promise<void> {
  error.value = ''
  try {
    await mentorApi.setTraineeActive(row.id, !row.active)
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Update failed'
  }
}

async function resetPw(row: TraineeResponse): Promise<void> {
  error.value = ''
  try {
    const res = await mentorApi.resetTraineePassword(row.id)
    window.alert(`New temporary password for ${res.email}: ${res.temporaryPassword}`)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Reset failed'
  }
}

function openAssign(id: number): void {
  assignTraineeId.value = id
  assignCurriculumId.value = curricula.value[0]?.id ?? null
}

async function confirmAssign(): Promise<void> {
  if (assignTraineeId.value == null || assignCurriculumId.value == null) return
  error.value = ''
  try {
    await mentorApi.assignCurriculum(assignTraineeId.value, assignCurriculumId.value)
    assignTraineeId.value = null
    window.alert('Curriculum assigned.')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Assign failed'
  }
}
</script>

<template>
  <div>
    <h1>Trainees</h1>
    <p class="muted">Search, create, activate/deactivate, reset password, assign curriculum.</p>

    <section class="card">
      <h2>Create trainee</h2>
      <div class="row">
        <label>
          Email
          <input v-model="createEmail" type="email" />
        </label>
        <label>
          Full name
          <input v-model="createName" />
        </label>
        <button type="button" @click="createTrainee">Create</button>
      </div>
      <p v-if="createMsg" class="ok">{{ createMsg }}</p>
    </section>

    <section class="card">
      <h2>Search</h2>
      <div class="row">
        <input v-model="query" placeholder="q" @keyup.enter="load" />
        <button type="button" @click="load">Search</button>
      </div>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">Loading…</p>

    <table v-else class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Email</th>
          <th>Name</th>
          <th>Active</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ r.email }}</td>
          <td>{{ r.fullName }}</td>
          <td>{{ r.active ? 'yes' : 'no' }}</td>
          <td class="actions">
            <button type="button" @click="toggleActive(r)">{{ r.active ? 'Deactivate' : 'Activate' }}</button>
            <button type="button" @click="resetPw(r)">Reset password</button>
            <button type="button" @click="openAssign(r.id)">Assign curriculum</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="assignTraineeId != null" class="modal">
      <div class="modal-card">
        <h3>Assign curriculum</h3>
        <label>
          Curriculum
          <select v-model.number="assignCurriculumId">
            <option v-for="c in curricula" :key="c.id" :value="c.id">
              {{ c.name }} ({{ c.status }})
            </option>
          </select>
        </label>
        <div class="row">
          <button type="button" @click="assignTraineeId = null">Cancel</button>
          <button type="button" @click="confirmAssign">Assign</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: #64748b;
}
.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
input,
select {
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
.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
th,
td {
  border-bottom: 1px solid #e2e8f0;
  padding: 0.5rem 0.6rem;
  text-align: left;
  font-size: 0.9rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.error {
  color: #b91c1c;
}
.ok {
  color: #15803d;
}
.modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.modal-card {
  background: #fff;
  padding: 1rem;
  border-radius: 8px;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
