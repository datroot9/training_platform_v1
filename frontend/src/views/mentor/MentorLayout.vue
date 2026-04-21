<script setup lang="ts">
import { ref } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import ChangePasswordDialog from '../../components/account/ChangePasswordDialog.vue'
import AppSidebar from '../../components/layout/AppSidebar.vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const changePasswordVisible = ref(false)
const primaryLinks = [
  { label: 'Dashboard', to: '/mentor', icon: 'dashboard' },
  { label: 'Trainees', to: '/mentor/trainees', icon: 'users' },
  { label: 'Reports', to: '/mentor/reports', icon: 'roadmap' },
  { label: 'Curricula', to: '/mentor/curricula', icon: 'curriculum', activePrefixes: ['/mentor/curricula/'] },
]
const secondaryLinks = [{ label: 'Change password', to: '/mentor', icon: 'security', action: 'open-change-password' }]

async function signOut(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}

function onSidebarAction(action: string): void {
  if (action === 'open-change-password') {
    changePasswordVisible.value = true
  }
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
      @action="onSidebarAction"
    />

    <main class="content">
      <section class="content-shell">
        <RouterView />
      </section>
    </main>

    <ChangePasswordDialog v-model:visible="changePasswordVisible" />
  </div>
</template>

<style scoped>
.layout {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--ui-sidebar-width) 1fr;
  background: var(--ui-bg-gradient);
  isolation: isolate;
}

.content {
  padding: 1.35rem 1.5rem 2rem;
  min-width: 0;
  position: relative;
}

.content-shell {
  min-height: calc(100vh - 2.7rem);
  padding: 1rem;
  max-width: 1320px;
  margin: 0 auto;
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-xl);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86) 0%, rgba(238, 244, 255, 0.88) 54%, rgba(255, 244, 240, 0.86) 100%);
  box-shadow: var(--ui-shadow-sm);
  backdrop-filter: blur(2px);
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .content {
    padding: 1rem;
  }
}
</style>
