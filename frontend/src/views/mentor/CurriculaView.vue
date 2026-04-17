<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '../../api/client'
import * as mentorApi from '../../api/modules/mentor'
import type { CurriculumResponse } from '../../api/types'

const rows = ref<CurriculumResponse[]>([])
const loading = ref(false)
const error = ref('')

const name = ref('')
const description = ref('')
const msg = ref('')

async function load(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    rows.value = await mentorApi.listCurricula()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Failed to load curricula'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function create(): Promise<void> {
  msg.value = ''
  error.value = ''
  try {
    await mentorApi.createCurriculum({ name: name.value, description: description.value })
    name.value = ''
    description.value = ''
    msg.value = 'Curriculum created.'
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Create failed'
  }
}
</script>

<template>
  <div>
    <h1>Curricula</h1>
    <p class="muted">Create draft curricula, then open a curriculum to upload PDFs and task templates.</p>

    <section class="card">
      <h2>New curriculum</h2>
      <label>
        Name
        <input v-model="name" />
      </label>
      <label>
        Description
        <textarea v-model="description" rows="3" />
      </label>
      <button type="button" @click="create">Create</button>
      <p v-if="msg" class="ok">{{ msg }}</p>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">Loading…</p>

    <ul v-else class="list">
      <li v-for="c in rows" :key="c.id">
        <RouterLink :to="`/mentor/curricula/${c.id}`">{{ c.name }}</RouterLink>
        <span class="pill">{{ c.status }}</span>
      </li>
    </ul>
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
.list {
  list-style: none;
  padding: 0;
  margin: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.list li {
  padding: 0.65rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
.list a {
  color: #2563eb;
}
.pill {
  font-size: 0.75rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #e2e8f0;
}
.error {
  color: #b91c1c;
}
.ok {
  color: #15803d;
}
</style>
