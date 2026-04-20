<script setup lang="ts">
import Button from 'primevue/button'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/layout/PageHeader.vue'

const router = useRouter()
</script>

<template>
  <div class="wrap">
    <PageHeader
      title="Dashboard"
      description="A full progress overview will be added later. For now, open your learning path or current assignment from below or the sidebar."
    />
    <div class="actions">
      <Button label="Curriculum roadmap" icon="pi pi-map" @click="router.push('/trainee/curriculum')" />
      <Button
        label="My assignment"
        severity="secondary"
        icon="pi pi-bookmark"
        @click="router.push('/trainee/assignment')"
      />
    </div>
  </div>
</template>

<style scoped>
.wrap {
  max-width: 52rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1rem;
}
</style>
