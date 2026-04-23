<script setup lang="ts">
import Drawer from 'primevue/drawer'
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import ChangePasswordDialog from '../../components/account/ChangePasswordDialog.vue'
import AppSidebar from '../../components/layout/AppSidebar.vue'
import { useMediaQuery } from '../../composables/useMediaQuery'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const changePasswordVisible = ref(false)
const isLargeScreen = useMediaQuery('(min-width: 1024px)')
const showDockedSidebar = computed(() => isLargeScreen.value)
const sidebarDrawerOpen = ref(false)
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

watch(isLargeScreen, (wide) => {
  if (wide) sidebarDrawerOpen.value = false
})

watch(
  () => route.fullPath,
  () => {
    if (!showDockedSidebar.value) {
      sidebarDrawerOpen.value = false
    }
  },
)

async function onSidebarLogout(): Promise<void> {
  if (!showDockedSidebar.value) sidebarDrawerOpen.value = false
  await signOut()
}

function onSidebarAction(action: string): void {
  if (!showDockedSidebar.value) sidebarDrawerOpen.value = false
  if (action === 'open-change-password') {
    changePasswordVisible.value = true
  }
}
</script>

<template>
  <div class="layout" :class="{ 'layout--docked': showDockedSidebar }">
    <AppSidebar
      v-if="showDockedSidebar"
      title="Mentor Space"
      :primary-links="primaryLinks"
      :secondary-links="secondaryLinks"
      :user-email="auth.user?.email"
      user-role="Mentor"
      display-mode="docked"
      @logout="onSidebarLogout"
      @action="onSidebarAction"
    />

    <main class="content">
      <section class="content-shell">
        <RouterView />
      </section>
    </main>

    <button
      v-if="!showDockedSidebar"
      type="button"
      class="mobile-nav-chip"
      aria-label="Open mentor sidebar"
      @click="sidebarDrawerOpen = true"
    >
      <span class="mobile-nav-icon" aria-hidden="true">≡</span>
      <span>Mentor menu</span>
    </button>

    <Drawer
      v-model:visible="sidebarDrawerOpen"
      position="left"
      header="Mentor space"
      :block-scroll="true"
      class="mentor-sidebar-drawer"
    >
      <AppSidebar
        title="Mentor Space"
        :primary-links="primaryLinks"
        :secondary-links="secondaryLinks"
        :user-email="auth.user?.email"
        user-role="Mentor"
        display-mode="drawer"
        @logout="onSidebarLogout"
        @action="onSidebarAction"
      />
    </Drawer>

    <ChangePasswordDialog v-model:visible="changePasswordVisible" />
  </div>
</template>

<style scoped>
.layout {
  position: relative;
  min-height: 100vh;
  height: 100vh;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  grid-template-columns: 1fr;
  background: var(--ui-bg-gradient);
  isolation: isolate;
  overflow: hidden;
}

.layout--docked {
  grid-template-columns: var(--ui-sidebar-width) 1fr;
}

.content {
  padding: 1.35rem 1.5rem 2rem;
  min-width: 0;
  min-height: 0;
  position: relative;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.content-shell {
  min-height: 100%;
  flex: 1;
  width: 100%;
  padding: 1rem;
  max-width: none;
  margin: 0;
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 28%, var(--ui-border-soft));
  border-radius: var(--ui-radius-xl);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, #ffffff 82%, var(--ui-accent-soft-2)) 0%,
    color-mix(in srgb, #ffffff 78%, var(--ui-accent-2-soft)) 52%,
    color-mix(in srgb, #ffffff 82%, var(--ui-coral-soft)) 100%
  );
  box-shadow:
    0 18px 36px -24px color-mix(in srgb, var(--ui-accent-2) 46%, transparent),
    0 8px 20px rgba(36, 44, 70, 0.12);
  backdrop-filter: blur(2px);
}

.mobile-nav-chip {
  position: fixed;
  left: max(0.9rem, env(safe-area-inset-left));
  bottom: max(0.9rem, env(safe-area-inset-bottom));
  z-index: 2100;
  border: 1px solid var(--ui-border-soft);
  border-radius: 999px;
  background: linear-gradient(145deg, var(--ui-surface), #f5edff);
  color: var(--ui-text-primary);
  font-weight: 700;
  font-size: 0.82rem;
  padding: 0.55rem 0.75rem;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  box-shadow: var(--ui-shadow-md);
  cursor: pointer;
}

.mobile-nav-icon {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 999px;
  background: linear-gradient(145deg, var(--tp-purple-500), var(--tp-pink-500));
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
}

.mobile-nav-chip:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring), var(--ui-shadow-md);
}

@media (max-width: 900px) {
  .content {
    padding: 1rem;
  }
}

</style>

<style>
.mentor-sidebar-drawer.p-drawer {
  width: min(22rem, calc(100vw - 1.5rem));
  max-width: 100vw;
}

.mentor-sidebar-drawer .sidebar {
  border-right: 0;
}
</style>
