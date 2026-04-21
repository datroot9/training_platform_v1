<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import * as accountApi from '../../api/modules/account'
import { ApiError } from '../../api/client'
import { useAuthStore } from '../../stores/auth'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  changed: []
}>()

const auth = useAuthStore()
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const message = ref('')
const error = ref('')
const loading = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const confirmMismatch = computed(() => {
  if (!confirmPassword.value) return false
  return newPassword.value !== confirmPassword.value
})

const submitDisabled = computed(() => {
  if (loading.value) return true
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) return true
  if (newPassword.value.length < 8) return true
  return confirmMismatch.value
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      message.value = ''
      error.value = ''
      return
    }
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    message.value = ''
    error.value = ''
    loading.value = false
  },
)

function closeDialog(): void {
  if (loading.value) return
  dialogVisible.value = false
}

async function submit(): Promise<void> {
  message.value = ''
  error.value = ''
  if (confirmMismatch.value) {
    error.value = 'Confirm password does not match new password.'
    return
  }
  loading.value = true
  try {
    await accountApi.changePassword(oldPassword.value, newPassword.value)
    auth.mustChangePassword = false
    emit('changed')
    dialogVisible.value = false
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Could not change password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    dismissable-mask
    close-on-escape
    :draggable="false"
    :style="{ width: 'min(520px, 92vw)' }"
    header="Change password"
    class="change-password-dialog"
    @hide="closeDialog"
  >
    <p class="dialog-description">Keep your account secure by setting a strong new password.</p>

    <form class="dialog-body" @submit.prevent="submit">
      <label>
        Current password
        <InputText
          v-model="oldPassword"
          type="password"
          required
          autocomplete="current-password"
          :disabled="loading"
        />
      </label>
      <label>
        New password (min 8)
        <InputText
          v-model="newPassword"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
          :disabled="loading"
        />
      </label>
      <label>
        Confirm new password
        <InputText
          v-model="confirmPassword"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
          :disabled="loading"
        />
      </label>

      <Message v-if="confirmMismatch" severity="warn" :closable="false">
        Confirm password does not match new password.
      </Message>
      <Message v-if="message" severity="success" :closable="false">{{ message }}</Message>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <footer class="dialog-footer">
        <Button label="Cancel" text severity="secondary" :disabled="loading" @click="closeDialog" />
        <Button type="submit" label="Save password" icon="pi pi-save" :loading="loading" :disabled="submitDisabled" />
      </footer>
    </form>
  </Dialog>
</template>

<style scoped>
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.85rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
  color: #334155;
  font-weight: 500;
}

.dialog-footer {
  margin-top: 0.35rem;
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.dialog-description {
  margin: 0;
  color: #64748b;
  font-size: 0.9rem;
  line-height: 1.45;
}

:deep(.change-password-dialog .p-dialog-header) {
  background: linear-gradient(135deg, #faf5ff 0%, #f8fafc 100%);
  border-bottom: 1px solid #ede9fe;
}

:deep(.change-password-dialog .p-dialog-content) {
  padding-top: 0.95rem;
}

@media (max-width: 640px) {
  .dialog-footer {
    flex-direction: column-reverse;
  }

  .dialog-footer :deep(.p-button) {
    width: 100%;
  }
}
</style>
