<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import AppSidebar from '../../components/layout/AppSidebar.vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const primaryLinks = [
  { label: 'Dashboard', to: '/mentor', icon: 'dashboard' },
  { label: 'Trainees', to: '/mentor/trainees', icon: 'users' },
  { label: 'Curricula', to: '/mentor/curricula', icon: 'curriculum', activePrefixes: ['/mentor/curricula/'] },
]
const secondaryLinks = [{ label: 'Account Security', to: '/account/change-password', icon: 'security' }]

async function signOut(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <AppSidebar
      title="Mentor Space"
      :primary-links="primaryLinks"
      :secondary-links="secondaryLinks"
      :user-email="auth.user?.email"
      user-role="Mentor"
      @logout="signOut"
    />

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px 1fr;
  background: #f8fafc;
}

.content {
  padding: 1.25rem;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
