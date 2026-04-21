<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ChangePasswordDialog from '../components/account/ChangePasswordDialog.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const visible = ref(true)
const closing = ref(false)

async function closeDialogAndBack(): Promise<void> {
  if (closing.value) return
  closing.value = true
  if (window.history.length > 1) {
    router.back()
    return
  }
  await router.replace(auth.user?.role === 'MENTOR' ? '/mentor' : '/trainee')
}
</script>

<template>
  <ChangePasswordDialog v-model:visible="visible" @update:visible="closeDialogAndBack" />
</template>
