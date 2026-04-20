<script setup lang="ts">
import Button from 'primevue/button'
import Card from 'primevue/card'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import PageHeader from '../../components/layout/PageHeader.vue'
import {
  injectTraineeAssignment,
  taskStatusLabel,
  taskStatusTagSeverity,
} from '../../composables/useTraineeAssignment'

const {
  assignment,
  tasks,
  loading,
  error,
  hasAssignment,
  completedTaskCount,
  totalTaskCount,
  progressPercent,
  downloadPdf,
} = injectTraineeAssignment()
</script>

<template>
  <div class="wrap">
    <PageHeader
      title="Curriculum roadmap"
      description="Your steps in order. Download PDFs when your mentor linked materials to a step."
      :tag-value="hasAssignment ? `${completedTaskCount}/${totalTaskCount} done` : undefined"
      :tag-severity="progressPercent === 100 ? 'success' : 'info'"
    />

    <Message v-if="error" severity="error" :closable="false" class="mb-msg">{{ error }}</Message>

    <div v-if="loading" class="centered">
      <ProgressSpinner stroke-width="3" animation-duration=".8s" />
    </div>

    <template v-else-if="!hasAssignment">
      <Message severity="info" :closable="false">
        You do not have an active curriculum assignment yet. When your mentor assigns one, your full roadmap will
        appear here.
      </Message>
    </template>

    <template v-else>
      <p class="curriculum-line">
        <strong>{{ assignment!.curriculumName }}</strong>
        <span class="muted"> · {{ assignment!.status }}</span>
      </p>

      <div class="timeline">
        <div v-for="t in tasks" :key="t.id" class="step-row">
          <div class="rail">
            <span class="dot" />
            <span class="line" />
          </div>
          <Card class="step-card">
            <template #title>
              <div class="card-title-row">
                <span class="step-num">Step {{ t.sortOrder }}</span>
                <Tag :value="taskStatusLabel(t.status)" :severity="taskStatusTagSeverity(t.status)" rounded />
              </div>
              <span class="task-title">{{ t.title }}</span>
            </template>
            <template #content>
              <p v-if="t.description" class="desc">{{ t.description }}</p>
              <Button
                v-if="t.learningMaterialId != null"
                label="Download PDF"
                icon="pi pi-download"
                size="small"
                outlined
                class="dl-btn"
                @click="downloadPdf(t.learningMaterialId!)"
              />
              <p v-else class="muted small">No PDF linked to this step.</p>
            </template>
          </Card>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wrap {
  max-width: 44rem;
}
.mb-msg {
  margin-top: 0.75rem;
}
.centered {
  display: flex;
  justify-content: center;
  padding: 2rem;
}
.curriculum-line {
  margin: 0.5rem 0 1.25rem;
  font-size: 1rem;
}
.muted {
  color: var(--text-muted);
}
.small {
  font-size: 0.85rem;
  margin: 0;
}
.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.step-row {
  display: grid;
  grid-template-columns: 1.5rem 1fr;
  gap: 0.75rem;
}
.rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 0.5rem;
}
.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--trainee-accent, #7c3aed);
  border: 2px solid #ede9fe;
  flex-shrink: 0;
}
.line {
  flex: 1;
  width: 2px;
  min-height: 1rem;
  background: linear-gradient(180deg, #ddd6fe, #e2e8f0);
  margin-top: 4px;
}
.step-row:last-child .line {
  display: none;
}
.step-card {
  margin-bottom: 0.75rem;
  border: 1px solid #e9d5ff;
  box-shadow: none;
}
.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.35rem;
}
.step-num {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--trainee-accent, #6d28d9);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.task-title {
  display: block;
  font-size: 1.05rem;
  font-weight: 600;
  color: #0f172a;
}
.desc {
  margin: 0 0 0.75rem;
  color: #475569;
  line-height: 1.5;
  font-size: 0.95rem;
}
.dl-btn {
  margin-top: 0.25rem;
}
</style>
