<script setup lang="ts">
import { computed, ref } from 'vue'
import ExpressionPanel from './ExpressionPanel.vue'
import { useShellStore } from '../../stores/shell'

const props = defineProps<{
  messageId: string
}>()

const shell = useShellStore()
const panelOpen = ref(false)
const reactions = computed(() => shell.reactionsForMessage(props.messageId))

const togglePanel = () => {
  panelOpen.value = !panelOpen.value
}

const closePanel = () => {
  panelOpen.value = false
}
</script>

<template>
  <div class="reaction-zone" :data-testid="`reaction-zone-${messageId}`">
    <div v-if="reactions.length" class="reaction-bar" aria-label="Message reactions">
      <button
        v-for="reaction in reactions"
        :key="reaction.emojiKey"
        class="reaction-chip"
        :class="{ 'reaction-chip-active': reaction.reactedByCurrentUser }"
        type="button"
        :data-testid="`reaction-chip-${messageId}-${reaction.emojiKey}`"
        :aria-pressed="reaction.reactedByCurrentUser"
        @click="shell.toggleReaction(messageId, reaction.emojiKey)"
      >
        <span aria-hidden="true">{{ reaction.symbol }}</span>
        <strong>{{ reaction.label }}</strong>
        <span>{{ reaction.count }}</span>
      </button>
    </div>
    <button
      class="expression-toggle"
      type="button"
      :data-testid="`expression-toggle-${messageId}`"
      :aria-expanded="panelOpen"
      :aria-controls="`expression-panel-${messageId}`"
      @click="togglePanel"
    >
      Add reaction
    </button>
    <ExpressionPanel v-if="panelOpen" :message-id="messageId" @close="closePanel" />
  </div>
</template>
