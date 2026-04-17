<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()

async function signOut(): Promise<void> {
  await auth.logout()
}
</script>

<template>
  <div class="layout">
    <header class="top">
      <div class="brand">Mentor</div>
      <nav class="nav">
        <RouterLink to="/mentor">Home</RouterLink>
        <RouterLink to="/mentor/trainees">Trainees</RouterLink>
        <RouterLink to="/mentor/curricula">Curricula</RouterLink>
        <RouterLink to="/account/change-password">Password</RouterLink>
      </nav>
      <div class="user">
        <span class="muted">{{ auth.user?.email }}</span>
        <button type="button" @click="signOut">Logout</button>
      </div>
    </header>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}
.top {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.25rem;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.brand {
  font-weight: 700;
}
.nav {
  display: flex;
  gap: 0.75rem;
  flex: 1;
}
.nav a {
  color: #2563eb;
  text-decoration: none;
}
.nav a.router-link-active {
  font-weight: 600;
  text-decoration: underline;
}
.user {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.muted {
  color: #64748b;
  font-size: 0.85rem;
}
button {
  padding: 0.35rem 0.6rem;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  background: #fff;
  cursor: pointer;
}
.main {
  padding: 1.25rem;
  max-width: 1100px;
  width: 100%;
  margin: 0 auto;
}
</style>
