<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import SidebarIcon from './SidebarIcon.vue'

type SidebarLink = {
  label: string
  to: string
  icon: string
  activePrefixes?: string[]
  action?: string
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
  action: [action: string]
}>()
const route = useRoute()

function onLogout(): void {
  emit('logout')
}

function onAction(action: string): void {
  emit('action', action)
}

function isActive(link: SidebarLink): boolean {
  if (route.path === link.to) return true
  return (link.activePrefixes ?? []).some((prefix) => route.path.startsWith(prefix))
}

function initials(email: string | undefined): string {
  const e = email?.trim()
  if (!e) return '?'
  const local = e.split('@')[0] ?? ''
  return local.slice(0, 2).toUpperCase() || '?'
}
</script>

<template>
  <aside class="sidebar" :class="`sidebar--${props.variant}`">
    <div class="sidebar-top">
      <div class="brand-wrap">
        <span class="brand-logo" aria-hidden="true">
          <span class="brand-logo-dot" />
        </span>
        <div class="brand-meta">
          <p class="brand">{{ props.title }}</p>
          <p class="role-pill">{{ props.userRole }} workspace</p>
        </div>
      </div>

      <nav class="nav" aria-label="Primary navigation">
        <template v-for="link in props.primaryLinks" :key="`${link.to}-${link.action ?? ''}`">
          <button v-if="link.action" type="button" class="nav-link" @click="onAction(link.action)">
            <span class="link-indicator" aria-hidden="true" />
            <span class="link-content">
              <SidebarIcon :name="link.icon" />
              <span>{{ link.label }}</span>
            </span>
          </button>
          <RouterLink v-else :to="link.to" class="nav-link" :class="{ 'is-active': isActive(link) }">
            <span class="link-indicator" aria-hidden="true" />
            <span class="link-content">
              <SidebarIcon :name="link.icon" />
              <span>{{ link.label }}</span>
            </span>
          </RouterLink>
        </template>
      </nav>
    </div>

    <div class="sidebar-bottom">
      <nav class="nav secondary" aria-label="Account actions">
        <template v-for="link in props.secondaryLinks" :key="`${link.to}-${link.action ?? ''}`">
          <button v-if="link.action" type="button" class="nav-link nav-link--muted" @click="onAction(link.action)">
            <span class="link-content">
              <SidebarIcon :name="link.icon" />
              <span>{{ link.label }}</span>
            </span>
          </button>
          <RouterLink
            v-else
            :to="link.to"
            class="nav-link nav-link--muted"
            :class="{ 'is-active': isActive(link) }"
          >
            <span class="link-content">
              <SidebarIcon :name="link.icon" />
              <span>{{ link.label }}</span>
            </span>
          </RouterLink>
        </template>
      </nav>

      <div class="user-card">
        <div class="user-avatar" aria-hidden="true">{{ initials(props.userEmail) }}</div>
        <div class="user-body">
          <p class="user-email">{{ props.userEmail || 'Unknown user' }}</p>
          <p class="user-role">{{ props.userRole }}</p>
        </div>
        <button type="button" class="icon-btn logout-icon" aria-label="Logout" @click="onLogout">
          <SidebarIcon name="logout" />
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  border-right: 1px solid var(--ui-border-soft);
  background: linear-gradient(180deg, #ffffff 0%, #fbf7ff 100%);
  padding: 1.1rem 0.9rem 1rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1.1rem;
  min-height: 100vh;
  position: relative;
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 140px;
  background: radial-gradient(600px 180px at 20% 0%, rgba(236, 72, 153, 0.14), transparent 70%),
    radial-gradient(500px 200px at 80% 0%, rgba(164, 53, 240, 0.18), transparent 70%);
  pointer-events: none;
}

.sidebar-top,
.sidebar-bottom {
  position: relative;
  z-index: 1;
}

.brand-wrap {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1.2rem;
}

.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--tp-purple-500) 0%, var(--tp-pink-500) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 24px -14px rgba(164, 53, 240, 0.6);
}

.brand-logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.3);
}

.brand-meta {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.brand {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 800;
  color: var(--ui-heading);
  letter-spacing: -0.01em;
}

.role-pill {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--ui-text-secondary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.nav-link,
.logout-btn {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  text-align: left;
  text-decoration: none;
  color: var(--ui-text-primary);
  background: transparent;
  border: 0;
  border-radius: 12px;
  padding: 0.6rem 0.7rem 0.6rem 0.95rem;
  font-size: 0.92rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--ui-transition-fast), color var(--ui-transition-fast),
    transform var(--ui-transition-fast);
}

.nav-link--muted {
  color: var(--ui-text-secondary);
  font-weight: 500;
}

.link-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--tp-purple-500) 0%, var(--tp-pink-500) 100%);
  transition: height var(--ui-transition-base);
}

.link-content {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.nav-link:hover {
  background: var(--ui-accent-soft);
  color: var(--ui-accent-deep);
}

.nav-link--muted:hover {
  color: var(--ui-text-primary);
}

.nav-link.is-active {
  background: var(--ui-accent-soft);
  color: var(--ui-accent-deep);
  font-weight: 700;
}

.nav-link.is-active .link-indicator {
  height: 60%;
}

.nav-link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring);
}

.secondary {
  border-top: 1px solid var(--ui-border-soft);
  padding-top: 0.7rem;
  margin-top: 0.5rem;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.75rem;
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-md);
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-xs);
  padding: 0.55rem 0.6rem;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(145deg, var(--tp-purple-500), var(--tp-pink-500));
  color: #ffffff;
  font-weight: 700;
  font-size: 0.78rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  letter-spacing: 0.02em;
}

.user-body {
  min-width: 0;
  flex: 1;
}

.user-email {
  margin: 0;
  font-size: 0.82rem;
  color: var(--ui-text-primary);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  margin: 0.15rem 0 0;
  font-size: 0.72rem;
  color: var(--ui-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.icon-btn {
  border: 0;
  background: transparent;
  color: var(--ui-text-secondary);
  padding: 0.35rem;
  border-radius: 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color var(--ui-transition-fast), color var(--ui-transition-fast);
}

.icon-btn:hover {
  background: var(--ui-danger-soft);
  color: var(--ui-danger);
}

.icon-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring);
}

.sidebar--trainee::before {
  background: radial-gradient(600px 180px at 20% 0%, rgba(236, 72, 153, 0.18), transparent 70%),
    radial-gradient(500px 200px at 80% 0%, rgba(124, 58, 237, 0.2), transparent 70%);
}

@media (max-width: 900px) {
  .sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--ui-border-soft);
    min-height: auto;
  }
}
</style>
