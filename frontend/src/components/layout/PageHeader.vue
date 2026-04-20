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
      <div class="heading-group">
        <p class="eyebrow">Learning workspace</p>
        <h1>{{ props.title }}</h1>
      </div>
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
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.15rem;
  border: 1px solid var(--ui-border-soft);
  border-radius: var(--ui-radius-lg);
  background: linear-gradient(135deg, #fff 0%, #fcfbff 72%);
  box-shadow: var(--ui-shadow-sm);
}

.left {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.heading-group {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.eyebrow {
  margin: 0;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ui-text-secondary);
}

.left h1 {
  margin: 0;
  font-size: clamp(1.35rem, 2.3vw, 1.95rem);
  line-height: 1.2;
  color: var(--ui-heading);
}

.description p {
  margin: 0;
  max-width: 74ch;
  color: var(--ui-text-secondary);
  line-height: 1.5;
  font-size: 0.94rem;
}

.right {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex-wrap: wrap;
}

.back-btn {
  width: fit-content;
  font-size: 0.86rem;
  font-weight: 600;
  padding: 0.38rem 0.82rem;
  background: var(--ui-accent);
  border: 1px solid var(--ui-accent);
  color: #fff;
  border-radius: 999px;
  transition: background-color var(--ui-transition-fast), border-color var(--ui-transition-fast),
    box-shadow var(--ui-transition-fast), transform var(--ui-transition-fast);
}

.back-btn:hover {
  background: var(--ui-accent-strong);
  border-color: var(--ui-accent-strong);
  transform: translateY(-1px);
}

.back-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring);
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    padding: 0.92rem 1rem;
  }

  .right {
    justify-content: flex-start;
  }
}
</style>
