<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import SidebarIcon from './SidebarIcon.vue'

type SidebarLink = {
  label: string
  to: string
  icon: string
  activePrefixes?: string[]
}

const props = withDefaults(
  defineProps<{
    title: string
    userEmail?: string
    userRole: string
    primaryLinks: SidebarLink[]
    secondaryLinks: SidebarLink[]
    variant?: 'mentor' | 'trainee'
  }>(),
  { variant: 'mentor' },
)

const emit = defineEmits<{
  logout: []
}>()
const route = useRoute()

function onLogout(): void {
  emit('logout')
}

function isActive(link: SidebarLink): boolean {
  if (route.path === link.to) return true
  return (link.activePrefixes ?? []).some((prefix) => route.path.startsWith(prefix))
}
</script>

<template>
  <aside class="sidebar" :class="`sidebar--${props.variant}`">
    <div>
      <div class="brand-wrap">
        <p class="brand">{{ props.title }}</p>
        <p class="role-pill">{{ props.userRole }}</p>
      </div>
      <nav class="nav">
        <RouterLink
          v-for="link in props.primaryLinks"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          :class="{ 'is-active': isActive(link) }"
        >
          <span class="link-content">
            <SidebarIcon :name="link.icon" />
            <span>{{ link.label }}</span>
          </span>
        </RouterLink>
      </nav>
    </div>

    <div>
      <nav class="nav secondary">
        <RouterLink
          v-for="link in props.secondaryLinks"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          :class="{ 'is-active': isActive(link) }"
        >
          <span class="link-content">
            <SidebarIcon :name="link.icon" />
            <span>{{ link.label }}</span>
          </span>
        </RouterLink>
        <button type="button" class="logout-btn" @click="onLogout">
          <span class="link-content">
            <SidebarIcon name="logout" />
            <span>Logout</span>
          </span>
        </button>
      </nav>

      <div class="user-card">
        <p class="user-email">{{ props.userEmail || 'Unknown user' }}</p>
        <p class="user-role">{{ props.userRole }}</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  border-right: 1px solid var(--ui-border);
  background: linear-gradient(180deg, #fcfbff 0%, var(--ui-surface) 36%);
  padding: 1.15rem 0.95rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1.1rem;
  min-height: 100vh;
}

.brand-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.95rem;
}

.brand {
  margin: 0;
  font-size: 1.04rem;
  font-weight: 700;
  color: var(--ui-heading);
  letter-spacing: 0.01em;
}

.role-pill {
  margin: 0;
  width: fit-content;
  padding: 0.14rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--ui-accent-strong);
  background: var(--ui-accent-soft);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.nav-link,
.logout-btn {
  text-align: left;
  text-decoration: none;
  color: var(--ui-text-primary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--ui-radius-sm);
  padding: 0.6rem 0.72rem;
  font-size: 0.92rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--ui-transition-fast), border-color var(--ui-transition-fast),
    color var(--ui-transition-fast), transform var(--ui-transition-fast);
}

.nav-link.is-active {
  background: var(--ui-accent-soft);
  border-color: color-mix(in srgb, var(--ui-accent) 24%, transparent);
  color: var(--ui-accent-strong);
  font-weight: 600;
  transform: translateX(2px);
}

.link-content {
  display: inline-flex;
  align-items: center;
  gap: 0.58rem;
}

.nav-link:hover,
.logout-btn:hover {
  background: color-mix(in srgb, var(--ui-accent-soft) 58%, white);
  border-color: color-mix(in srgb, var(--ui-accent) 16%, transparent);
}

.nav-link:focus-visible,
.logout-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring);
}

.secondary {
  border-top: 1px solid var(--ui-border-soft);
  padding-top: 0.85rem;
}

.user-card {
  margin-top: 0.8rem;
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-md);
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  padding: 0.72rem 0.78rem;
}

.user-email {
  margin: 0;
  font-size: 0.86rem;
  color: var(--ui-text-primary);
  word-break: break-word;
  font-weight: 600;
}

.user-role {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: var(--ui-text-secondary);
}

.sidebar--trainee {
  background: linear-gradient(180deg, #f7f4ff 0%, #ffffff 45%);
  border-right-color: color-mix(in srgb, var(--ui-accent) 20%, var(--ui-border));
}

.sidebar--trainee .brand {
  color: #5b21b6;
}

.sidebar--trainee .role-pill {
  background: #ede9fe;
  color: #5b21b6;
}

.sidebar--trainee .nav-link.is-active {
  background: #f3e8ff;
  border-color: rgba(109, 40, 217, 0.28);
  color: #6d28d9;
  font-weight: 600;
}

.sidebar--trainee .nav-link:hover,
.sidebar--trainee .logout-btn:hover {
  background: #ede9fe;
  border-color: rgba(109, 40, 217, 0.2);
}

.sidebar--trainee .user-card {
  border-color: #ddd6fe;
  background: #fff;
}

.sidebar--trainee .secondary {
  border-top-color: #ddd6fe;
}

@media (max-width: 900px) {
  .sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--ui-border);
    min-height: auto;
  }

  .sidebar--trainee {
    border-bottom-color: #ddd6fe;
  }
}
</style>
