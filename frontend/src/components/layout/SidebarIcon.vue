<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  name: string
}>()

const path = computed(() => {
  switch (props.name) {
    case 'home':
      return 'M3 10.5 12 3l9 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-5.25v-6h-4.5v6H4.5A1.5 1.5 0 0 1 3 19.5z'
    case 'users':
      return 'M16.5 8.25a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM6.75 9.75a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5zM3 18a4.5 4.5 0 0 1 9 0v.75H3zm10.5.75V18c0-1.47-.53-2.82-1.41-3.87A4.5 4.5 0 0 1 21 18v.75z'
    case 'curriculum':
      return 'M5.25 4.5h13.5A1.5 1.5 0 0 1 20.25 6v12a1.5 1.5 0 0 1-1.5 1.5H5.25A1.5 1.5 0 0 1 3.75 18V6a1.5 1.5 0 0 1 1.5-1.5zM7.5 8.25h9m-9 3h9m-9 3h6'
    case 'dashboard':
      return 'M4.5 3.75h6.75v6.75H4.5zm8.25 0h6.75v4.5h-6.75zM4.5 12h4.5v8.25H4.5zm6 4.5h9v3.75h-9z'
    case 'roadmap':
      return 'M4.5 6.75h6.75a2.25 2.25 0 1 1 0 4.5H9a2.25 2.25 0 1 0 0 4.5h10.5M4.5 6.75A2.25 2.25 0 1 0 4.5 11.25M19.5 15.75a2.25 2.25 0 1 0 0 4.5'
    case 'assignment':
      return 'M7.5 4.5h9A1.5 1.5 0 0 1 18 6v12a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 18V6a1.5 1.5 0 0 1 1.5-1.5zM9 9h6m-6 3h6m-6 3h3'
    case 'security':
      return 'M12 3.75 4.5 6v5.25c0 5.06 3.39 9.75 7.5 10.5 4.11-.75 7.5-5.44 7.5-10.5V6zM9.75 12l1.8 1.8 3.9-3.9'
    case 'logout':
      return 'M15.75 3.75h3a1.5 1.5 0 0 1 1.5 1.5v13.5a1.5 1.5 0 0 1-1.5 1.5h-3M10.5 16.5 6 12m0 0 4.5-4.5M6 12h9.75'
    default:
      return ''
  }
})
</script>

<template>
  <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="path" />
  </svg>
</template>

<style scoped>
.icon {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
}
</style>
