<script setup lang="ts">
import Toast from 'primevue/toast'
import Drawer from 'primevue/drawer'
import { computed, onMounted, provide, ref, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'
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
</script>

<template>
  <div class="layout trainee-shell" :class="{ 'layout--dock-rail': showDockedRail }">
    <div class="workspace">
      <aside v-if="showDockedRail" class="left-rail" aria-label="Trainee tools">
        <TraineeRightRailContent variant="dock" @logout="signOut" />
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
      <TraineeRightRailContent variant="drawer" @dismiss="drawerOpen = false" @logout="signOut" />
    </Drawer>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: block;
  background: linear-gradient(135deg, #f6f1ff 0%, #f8f8ff 34%, #edf2ff 100%);
}

.trainee-shell {
  --trainee-accent: #7c3aed;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  min-height: 100vh;
}

.layout--dock-rail .workspace {
  grid-template-columns: minmax(250px, 280px) minmax(0, 1fr);
}

.content {
  padding: 1.1rem 1.15rem;
  min-width: 0;
}

.left-rail {
  border-right: 1px solid #ddd6fe;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(4px);
  padding: 1rem 0.9rem;
  overflow: auto;
  min-height: 0;
  position: sticky;
  top: 0;
  height: 100vh;
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
  background: linear-gradient(145deg, #ede9fe, #fff);
  border: 2px solid #c4b5fd;
}

.fab-avatar {
  position: relative;
  z-index: 1;
  font-size: 0.78rem;
  font-weight: 800;
  color: #5b21b6;
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
