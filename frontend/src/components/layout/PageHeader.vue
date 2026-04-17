<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    showBack?: boolean
    backTo?: string
    backLabel?: string
    tagValue?: string
    tagSeverity?: TagSeverity
  }>(),
  {
    description: '',
    showBack: false,
    backTo: '',
    backLabel: 'Back',
    tagValue: '',
    tagSeverity: 'info',
  },
)

const router = useRouter()

function onBack(): void {
  if (props.backTo) {
    void router.push(props.backTo)
    return
  }
  void router.back()
}
</script>

<template>
  <header class="page-header">
    <div class="left">
      <Button v-if="props.showBack" :label="props.backLabel" class="back-btn" @click="onBack" />
      <h1>{{ props.title }}</h1>
      <div v-if="$slots.description || props.description" class="description">
        <slot name="description">
          <p>{{ props.description }}</p>
        </slot>
      </div>
    </div>

    <div class="right">
      <Tag v-if="props.tagValue" :value="props.tagValue" :severity="props.tagSeverity" rounded />
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.left h1 {
  margin: 0;
  font-size: 1.9rem;
}

.description p {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.back-btn {
  width: fit-content;
  font-size: 0.9rem;
  padding: 0.35rem 0.8rem;
  background: var(--brand-600);
  border: 1px solid var(--brand-600);
  color: #fff;
}

.back-btn:hover {
  background: var(--brand-700);
  border-color: var(--brand-700);
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .right {
    justify-content: flex-start;
  }
}
</style>
