<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div>
    <h1>Dashboard</h1>
    <p class="muted">Manage trainees, curricula, and assignments.</p>
    <ul class="links">
      <li><RouterLink to="/mentor/trainees">Trainees</RouterLink></li>
      <li><RouterLink to="/mentor/curricula">Curricula</RouterLink></li>
    </ul>
  </div>
</template>

<style scoped>
.muted {
  color: #64748b;
}
.links {
  padding-left: 1.25rem;
}
.links a {
  color: #2563eb;
}
</style>
