<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import * as accountApi from '../api/modules/account'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'

const oldPassword = ref('')
const newPassword = ref('')
const message = ref('')
const error = ref('')
const loading = ref(false)

const auth = useAuthStore()
const router = useRouter()

async function submit(): Promise<void> {
  message.value = ''
  error.value = ''
  loading.value = true
  try {
    await accountApi.changePassword(oldPassword.value, newPassword.value)
    message.value = 'Password updated.'
    auth.mustChangePassword = false
    await router.replace(auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not change password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1>Change password</h1>
    <form class="card" @submit.prevent="submit">
      <label>
        Current password
        <input v-model="oldPassword" type="password" required autocomplete="current-password" />
      </label>
      <label>
        New password (min 8)
        <input v-model="newPassword" type="password" required minlength="8" autocomplete="new-password" />
      </label>
      <p v-if="message" class="ok">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">{{ loading ? 'Saving…' : 'Save' }}</button>
    </form>
  </div>
</template>

<style scoped>
.page {
  max-width: 480px;
  margin: 2rem auto;
  padding: 0 1rem;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}
input {
  padding: 0.5rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}
button {
  padding: 0.55rem 0.75rem;
  border-radius: 6px;
  border: none;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}
.error {
  color: #b91c1c;
}
.ok {
  color: #15803d;
}
</style>
