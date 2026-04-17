<script setup lang="ts">
import { RouterLink } from 'vue-router'
import SidebarIcon from './SidebarIcon.vue'

type SidebarLink = {
  label: string
  to: string
  icon: string
}

const props = defineProps<{
  title: string
  userEmail?: string
  userRole: string
  primaryLinks: SidebarLink[]
  secondaryLinks: SidebarLink[]
}>()

const emit = defineEmits<{
  logout: []
}>()

function onLogout(): void {
  emit('logout')
}
</script>

<template>
  <aside class="sidebar">
    <div>
      <p class="brand">{{ props.title }}</p>
      <nav class="nav">
        <RouterLink v-for="link in props.primaryLinks" :key="link.to" :to="link.to">
          <span class="link-content">
            <SidebarIcon :name="link.icon" />
            <span>{{ link.label }}</span>
          </span>
        </RouterLink>
      </nav>
    </div>

    <div>
      <nav class="nav secondary">
        <RouterLink v-for="link in props.secondaryLinks" :key="link.to" :to="link.to">
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
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 1rem 0.9rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 1rem;
}

.brand {
  margin: 0 0 1rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: #0f172a;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.nav a,
.logout-btn {
  text-align: left;
  text-decoration: none;
  color: #334155;
  background: transparent;
  border: 0;
  border-radius: 10px;
  padding: 0.55rem 0.7rem;
  font-size: 0.92rem;
  cursor: pointer;
}

.nav a.router-link-exact-active {
  background: var(--brand-50);
  color: var(--brand-600);
  font-weight: 600;
}

.link-content {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
}

.nav a:hover,
.logout-btn:hover {
  background: #f1f5f9;
}

.secondary {
  border-top: 1px solid #e2e8f0;
  padding-top: 0.8rem;
}

.user-card {
  margin-top: 0.7rem;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 0.65rem 0.75rem;
}

.user-email {
  margin: 0;
  font-size: 0.86rem;
  color: #0f172a;
  word-break: break-word;
}

.user-role {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  color: var(--text-muted);
}

@media (max-width: 900px) {
  .sidebar {
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
  }
}
</style>
