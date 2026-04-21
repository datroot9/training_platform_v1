<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import AppSidebar from '../../components/layout/AppSidebar.vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const primaryLinks = [
  { label: 'Dashboard', to: '/mentor', icon: 'dashboard' },
  { label: 'Trainees', to: '/mentor/trainees', icon: 'users' },
  { label: 'Reports', to: '/mentor/reports', icon: 'roadmap' },
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
      <section class="content-shell">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--ui-sidebar-width) 1fr;
  background: var(--ui-bg-gradient);
}

.content {
  padding: 1.1rem 1.15rem;
}

.content-shell {
  min-height: calc(100vh - 2.2rem);
  padding: 0.2rem;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .content {
    padding: 0.95rem;
  }
}
</style>
