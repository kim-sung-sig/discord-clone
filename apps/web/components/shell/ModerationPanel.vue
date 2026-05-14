<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const isHydrated = ref(false)

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <aside class="moderation-panel" data-testid="moderation-panel" aria-label="Moderation">
    <header class="moderation-panel-header">
      <p>Moderation</p>
      <h2>Safety operations</h2>
    </header>

    <section class="moderation-card">
      <p data-testid="onboarding-question">{{ shell.moderation.onboardingQuestion.prompt }}</p>
      <button
        v-for="answer in shell.moderation.onboardingQuestion.answers"
        :key="answer.id"
        type="button"
        :data-testid="`submit-onboarding-answer-${answer.id}`"
        :disabled="!isHydrated"
        @click="shell.submitOnboardingAnswer(answer.id)"
      >
        {{ answer.label }}
      </button>
      <strong data-testid="onboarding-assigned-role">
        Assigned role: {{ shell.moderation.assignedRoleName }}
      </strong>
    </section>

    <section class="moderation-card">
      <p>AutoMod rules</p>
      <span
        v-for="rule in shell.moderation.automodRules"
        :key="rule.id"
        class="automod-rule-chip"
        :data-testid="`automod-rule-${rule.id}`"
      >
        {{ rule.keyword }}
      </span>
      <button
        type="button"
        data-testid="simulate-automod-block"
        :disabled="!isHydrated"
        @click="shell.simulateAutoModBlock()"
      >
        Simulate blocked message
      </button>
      <strong data-testid="automod-decision">{{ shell.moderation.decision }}</strong>
    </section>

    <section class="moderation-card audit-log" data-testid="audit-log" aria-label="Audit log">
      <p>Audit log</p>
      <article
        v-for="entry in shell.moderation.auditLogs"
        :key="entry.id"
        class="audit-log-entry"
      >
        <strong>{{ entry.action }}</strong>
        <small>{{ entry.detail }}</small>
      </article>
    </section>
  </aside>
</template>
