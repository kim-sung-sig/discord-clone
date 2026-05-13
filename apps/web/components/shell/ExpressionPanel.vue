<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const props = defineProps<{
  messageId: string
}>()

const emit = defineEmits<{
  close: []
}>()

const shell = useShellStore()

const selectExpression = (emojiKey: string) => {
  shell.addReaction(props.messageId, emojiKey)
  emit('close')
}
</script>

<template>
  <section
    class="expression-panel"
    :data-testid="`expression-panel-${messageId}`"
    role="dialog"
    aria-label="Expressions"
  >
    <header class="expression-panel-header">
      <p>Expressions</p>
      <button type="button" :data-testid="`expression-close-${messageId}`" @click="emit('close')">
        Close
      </button>
    </header>
    <div class="expression-options" aria-label="Available expressions">
      <button
        v-for="expression in shell.expressions"
        :key="expression.key"
        class="expression-option"
        type="button"
        :data-testid="`expression-option-${messageId}-${expression.key}`"
        @click="selectExpression(expression.key)"
      >
        <span>{{ expression.symbol }}</span>
        <strong>{{ expression.label }}</strong>
        <small>{{ expression.description }}</small>
      </button>
    </div>
  </section>
</template>
