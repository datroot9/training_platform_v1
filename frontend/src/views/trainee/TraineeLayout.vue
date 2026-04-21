<script setup lang="ts">
import Toast from 'primevue/toast'
import Drawer from 'primevue/drawer'
import { computed, onMounted, provide, ref, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import ChangePasswordDialog from '../../components/account/ChangePasswordDialog.vue'
import TraineeRightRailContent from '../../components/trainee/TraineeRightRailContent.vue'
import { useMediaQuery } from '../../composables/useMediaQuery'
import { traineeAssignmentContextKey, useTraineeAssignment } from '../../composables/useTraineeAssignment'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const assignmentCtx = useTraineeAssignment()
provide(traineeAssignmentContextKey, assignmentCtx)

const isLargeScreen = useMediaQuery('(min-width: 1024px)')
const showDockedRail = computed(() => isLargeScreen.value)
const drawerOpen = ref(false)
const changePasswordVisible = ref(false)

watch(isLargeScreen, (wide) => {
  if (wide) drawerOpen.value = false
})

onMounted(() => {
  void assignmentCtx.load()
})

async function signOut(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}

function openChangePasswordDialog(): void {
  drawerOpen.value = false
  changePasswordVisible.value = true
}
</script>

<template>
  <div class="layout trainee-shell" :class="{ 'layout--dock-rail': showDockedRail }">
    <div class="workspace">
      <aside v-if="showDockedRail" class="left-rail" aria-label="Trainee tools">
        <TraineeRightRailContent variant="dock" @logout="signOut" @open-change-password="openChangePasswordDialog" />
      </aside>

      <main class="content">
        <Toast position="bottom-right" />
        <RouterView />
      </main>
    </div>

    <button
      v-if="!showDockedRail"
      type="button"
      class="fab-open"
      aria-label="Open your overview panel"
      @click="drawerOpen = true"
    >
      <span class="fab-ring" aria-hidden="true" />
      <span class="fab-avatar">{{ (auth.user?.email?.split('@')[0] ?? '?').slice(0, 2).toUpperCase() }}</span>
    </button>

    <Drawer
      v-model:visible="drawerOpen"
      position="left"
      header="Your overview"
      :block-scroll="true"
      class="trainee-overview-drawer"
    >
      <TraineeRightRailContent
        variant="drawer"
        @dismiss="drawerOpen = false"
        @logout="signOut"
        @open-change-password="openChangePasswordDialog"
      />
    </Drawer>

    <ChangePasswordDialog v-model:visible="changePasswordVisible" />
  </div>
</template>

<style scoped>
.layout {
  position: relative;
  min-height: 100vh;
  display: block;
  background: var(--ui-bg-gradient);
  isolation: isolate;
}

.trainee-shell {
  --trainee-accent: var(--tp-purple-500);
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  min-height: 100vh;
}

.layout--dock-rail .workspace {
  grid-template-columns: minmax(260px, 288px) minmax(0, 1fr);
}

.content {
  padding: 1.35rem 1.5rem 2rem;
  min-width: 0;
  max-width: 1320px;
  margin: 0 auto;
  width: 100%;
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-xl);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86) 0%, rgba(238, 244, 255, 0.88) 54%, rgba(255, 244, 240, 0.86) 100%);
  box-shadow: var(--ui-shadow-sm);
  backdrop-filter: blur(2px);
}

.left-rail {
  border-right: 1px solid var(--ui-border);
  background: linear-gradient(180deg, #ffffff 0%, #f2f5ff 58%, #fff5f0 100%);
  padding: 1rem 0.9rem;
  overflow: auto;
  min-height: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.55), 6px 0 20px -16px rgba(47, 56, 80, 0.28);
}

.fab-open {
  position: fixed;
  left: max(1rem, env(safe-area-inset-left));
  bottom: max(1rem, env(safe-area-inset-bottom));
  z-index: 2100;
  width: 3.35rem;
  height: 3.35rem;
  border: none;
  border-radius: 50%;
  padding: 0;
  cursor: pointer;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(109, 40, 217, 0.32);
  transition: transform var(--ui-transition-fast), box-shadow var(--ui-transition-fast);
}

.fab-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--tp-purple-500), var(--ui-coral));
  border: 2px solid rgba(255, 255, 255, 0.9);
}

.fab-avatar {
  position: relative;
  z-index: 1;
  font-size: 0.78rem;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: -0.02em;
}

.fab-open:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(109, 40, 217, 0.32);
}

.fab-open:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.25), 0 14px 28px rgba(109, 40, 217, 0.28);
}

@media (max-width: 900px) {
  .content {
    padding: 1rem;
    border-radius: var(--ui-radius-lg);
  }
}
</style>

<style>
/* Drawer width: PrimeVue root may be on portal — unscoped */
.trainee-overview-drawer.p-drawer {
  width: min(22rem, calc(100vw - 1.5rem));
  max-width: 100vw;
}
</style>
