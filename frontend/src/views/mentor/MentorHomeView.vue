<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PageHeader from '../../components/layout/PageHeader.vue'

type QuickCard = {
  to: string
  label: string
  description: string
  icon: string
  color: 'purple' | 'pink' | 'indigo'
}

const quickCards: QuickCard[] = [
  {
    to: '/mentor/trainees',
    label: 'Trainees',
    description: 'Assign curricula, track progress, and grade fresher outputs.',
    icon: 'pi-users',
    color: 'purple',
  },
  {
    to: '/mentor/reports',
    label: 'Reports',
    description: 'Review weekly summaries and daily logs from your cohort.',
    icon: 'pi-chart-line',
    color: 'pink',
  },
  {
    to: '/mentor/curricula',
    label: 'Curricula',
    description: 'Author, version and publish training tracks with materials.',
    icon: 'pi-book',
    color: 'indigo',
  },
]
</script>

<template>
  <div class="mentor-home">
    <PageHeader title="Mentor dashboard" description="Manage trainees, curricula, and assignments in one place.">
    </PageHeader>

    <section class="quick-grid">
      <RouterLink
        v-for="card in quickCards"
        :key="card.to"
        :to="card.to"
        class="quick-card"
        :class="`quick-card--${card.color}`"
      >
        <span class="quick-card__icon" aria-hidden="true">
          <i :class="['pi', card.icon]" />
        </span>
        <div class="quick-card__body">
          <h3>{{ card.label }}</h3>
          <p>{{ card.description }}</p>
        </div>
        <span class="quick-card__arrow" aria-hidden="true">
          <i class="pi pi-arrow-right" />
        </span>
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.mentor-home {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1rem;
}

.quick-card {
  position: relative;
  display: grid;
  grid-template-columns: 52px 1fr auto;
  align-items: center;
  gap: 0.9rem;
  padding: 1.15rem 1.15rem;
  background: linear-gradient(150deg, #ffffff 0%, color-mix(in srgb, #ffffff 80%, var(--ui-accent-soft-2)) 100%);
  border: 1px solid color-mix(in srgb, var(--ui-accent-2) 28%, var(--ui-border-soft));
  border-radius: var(--ui-radius-md);
  text-decoration: none;
  color: var(--ui-text-primary);
  box-shadow: var(--ui-shadow-xs);
  transition: transform var(--ui-transition-base), box-shadow var(--ui-transition-base),
    border-color var(--ui-transition-base);
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 16px 32px -22px color-mix(in srgb, var(--ui-accent-2) 54%, transparent),
    var(--ui-shadow-md);
  border-color: color-mix(in srgb, var(--ui-accent) 42%, var(--ui-border));
}

.quick-card:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ui-focus-ring), var(--ui-shadow-md);
}

.quick-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, color-mix(in srgb, var(--ui-accent-soft) 72%, #ffffff), var(--ui-accent-soft-2));
  color: var(--ui-accent-deep);
  font-size: 1.35rem;
}

.quick-card--pink .quick-card__icon {
  background: linear-gradient(145deg, color-mix(in srgb, var(--ui-pink-soft) 72%, #ffffff), color-mix(in srgb, var(--ui-pink-soft) 95%, var(--ui-accent-soft)));
  color: var(--tp-pink-600);
}

.quick-card--indigo .quick-card__icon {
  background: #eef2ff;
  color: #4f46e5;
}

.quick-card__body h3 {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--ui-heading);
  letter-spacing: -0.01em;
}

.quick-card__body p {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--ui-text-secondary);
}

.quick-card__arrow {
  color: var(--ui-text-muted);
  transition: transform var(--ui-transition-base), color var(--ui-transition-base);
}

.quick-card:hover .quick-card__arrow {
  color: var(--ui-accent-strong);
  transform: translateX(3px);
}

@media (max-width: 600px) {
  .quick-card {
    grid-template-columns: 44px 1fr;
  }

  .quick-card__arrow {
    display: none;
  }
}
</style>
