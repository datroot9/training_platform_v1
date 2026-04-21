<script setup lang="ts">
import { computed } from 'vue'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import ProgressSpinner from 'primevue/progressspinner'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { injectTraineeAssignment } from '../../composables/useTraineeAssignment'
import { useAuthStore } from '../../stores/auth'

const props = withDefaults(defineProps<{ variant?: 'dock' | 'drawer' }>(), { variant: 'dock' })

const emit = defineEmits<{
  dismiss: []
  logout: []
  openChangePassword: []
}>()

const auth = useAuthStore()
const router = useRouter()
const {
  assignment,
  tasks,
  loading,
  error,
  hasAssignment,
  completedTaskCount,
  totalTaskCount,
  progressPercent,
  load,
} = injectTraineeAssignment()

const email = computed(() => auth.user?.email ?? '')

const initials = computed(() => {
  const e = email.value
  if (!e) return '?'
  const local = e.split('@')[0] ?? ''
  const parts = local.split(/[._-]+/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  return local.slice(0, 2).toUpperCase() || '?'
})

const displayName = computed(() => {
  const e = email.value
  if (!e) return 'Trainee'
  const local = e.split('@')[0] ?? 'Trainee'
  const first = local.split(/[._-]/)[0] ?? local
  return first.charAt(0).toUpperCase() + first.slice(1)
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
})

const currentFocusTask = computed(() => tasks.value.find((task) => task.status !== 'DONE') ?? null)

function go(to: RouteLocationRaw): void {
  void router.push(to)
  if (props.variant === 'drawer') {
    emit('dismiss')
  }
}

function onLogout(): void {
  emit('logout')
  if (props.variant === 'drawer') {
    emit('dismiss')
  }
}

function onOpenChangePassword(): void {
  emit('openChangePassword')
  if (props.variant === 'drawer') {
    emit('dismiss')
  }
}
</script>

<template>
  <div class="rail-inner">
    <div class="profile-block card">
      <div class="donut-wrap" aria-hidden="true">
        <div
          class="donut"
          :style="{
            background: `conic-gradient(var(--trainee-accent, var(--tp-purple-500)) ${progressPercent}%, var(--tp-ink-100) 0)`,
          }"
        >
          <div class="donut-hole">
            <Avatar :label="initials" shape="circle" class="avatar-in-donut" />
          </div>
        </div>
      </div>
      <p class="greet">{{ greeting }}, {{ displayName }}</p>
      <p class="sub">Continue your learning path at your own pace.</p>
    </div>

    <div v-if="loading" class="spin-wrap">
      <ProgressSpinner stroke-width="3" animation-duration=".8s" style="width: 2.5rem; height: 2.5rem" />
    </div>
    <template v-else>
      <Message v-if="error" severity="error" :closable="false" class="msg">{{ error }}</Message>

      <section v-if="hasAssignment" class="progress-block card">
        <p class="section-label">Your progress</p>
        <p class="curriculum-name">{{ assignment!.curriculumName }}</p>
        <ProgressBar :value="progressPercent" :show-value="false" class="bar" />
        <p class="progress-caption">{{ completedTaskCount }} / {{ totalTaskCount }} tasks completed</p>
      </section>

      <Message v-else severity="info" :closable="false" class="msg">
        No active assignment yet. When your mentor assigns a curriculum, your progress will show here.
      </Message>

      <section class="focus-block card">
        <p class="section-label">Current focus</p>
        <template v-if="hasAssignment && currentFocusTask">
          <p class="focus-title">Step {{ currentFocusTask.sortOrder }} · {{ currentFocusTask.title }}</p>
          <p class="focus-meta">
            {{ currentFocusTask.learningMaterialId != null ? 'PDF attached' : 'No PDF attached' }}
          </p>
        </template>
        <template v-else-if="hasAssignment">
          <p class="focus-title">All tasks completed</p>
          <p class="focus-meta">Nice work. Keep this pace and check back for your next assignment.</p>
        </template>
        <template v-else>
          <p class="focus-title">Awaiting assignment</p>
          <p class="focus-meta">Your mentor will assign a curriculum to get you started.</p>
        </template>
      </section>

      <section class="links card">
        <p class="section-label">Quick links</p>
        <Button
          label="My assignment"
          icon="pi pi-bookmark"
          class="w-full primary-link-btn"
          outlined
          severity="secondary"
          @click="go({ name: 'trainee-assignment' })"
        />
        <Button
          label="Daily report"
          icon="pi pi-calendar"
          class="w-full primary-link-btn"
          outlined
          severity="secondary"
          @click="go({ name: 'trainee-daily-report' })"
        />
        <Button
          label="Change password"
          icon="pi pi-shield"
          class="w-full secondary-link-btn"
          text
          @click="onOpenChangePassword"
        />
      </section>

      <Button
        label="Logout"
        icon="pi pi-sign-out"
        class="w-full logout-btn"
        severity="danger"
        text
        @click="onLogout"
      />

      <Button label="Refresh status" icon="pi pi-refresh" class="w-full refresh" text size="small" @click="load()" />
    </template>
  </div>
</template>

<style scoped>
.rail-inner {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100%;
}

.card {
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-md);
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-xs);
}

.profile-block {
  text-align: center;
  padding: 0.85rem 0.85rem 0.95rem;
}

.donut-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: 0.75rem;
}

.donut {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  position: relative;
  padding: 8px;
}

.donut-hole {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--ui-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-in-donut {
  background: linear-gradient(145deg, var(--tp-purple-500), var(--tp-pink-500));
  color: #ffffff;
  font-weight: 700;
}

.greet {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--ui-heading);
}

.sub {
  margin: 0.35rem 0 0;
  font-size: 0.82rem;
  color: var(--ui-text-secondary);
  line-height: 1.4;
}

.spin-wrap {
  display: flex;
  justify-content: center;
  padding: 1rem;
}

.section-label {
  margin: 0 0 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ui-text-secondary);
}

.progress-block {
  padding: 0.85rem 1rem;
}

.focus-block {
  padding: 0.85rem 1rem;
}

.curriculum-name {
  margin: 0 0 0.5rem;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--ui-text-primary);
  line-height: 1.35;
}

.bar {
  height: 0.5rem;
}

.progress-caption {
  margin: 0.45rem 0 0;
  font-size: 0.8rem;
  color: var(--ui-text-secondary);
}

.focus-title {
  margin: 0;
  color: var(--ui-text-primary);
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.35;
}

.focus-meta {
  margin: 0.35rem 0 0;
  color: var(--ui-text-secondary);
  font-size: 0.8rem;
}

.links {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.85rem 0.8rem 0.8rem;
}

.w-full {
  width: 100%;
  justify-content: center;
}

.msg {
  margin: 0;
}

.refresh {
  margin-top: 0;
  opacity: 0.85;
}

.logout-btn {
  margin-top: auto;
}

:deep(.bar .p-progressbar-value) {
  background: linear-gradient(90deg, var(--tp-purple-500) 0%, var(--tp-pink-500) 100%);
}

:deep(.msg.p-message) {
  border-radius: var(--ui-radius-md);
}

:deep(.primary-link-btn.p-button) {
  border-color: var(--ui-border);
  color: var(--ui-text-primary);
  background: var(--ui-surface);
}

:deep(.primary-link-btn.p-button:hover) {
  background: var(--ui-accent-soft);
  border-color: color-mix(in srgb, var(--ui-accent) 30%, var(--ui-border));
  color: var(--ui-accent-deep);
}

:deep(.secondary-link-btn.p-button) {
  color: var(--ui-text-secondary);
}

:deep(.secondary-link-btn.p-button:hover) {
  background: var(--ui-accent-soft);
  color: var(--ui-accent-deep);
}

:deep(.logout-btn.p-button) {
  border-radius: 999px;
}
</style>
