<script setup lang="ts">
import { computed } from 'vue'
import type { ShellAttachment } from '../../stores/shell'

const props = defineProps<{
  attachment: ShellAttachment
  removable?: boolean
}>()

const emit = defineEmits<{
  (event: 'remove'): void
}>()

const sizeLabel = computed(() => `${(props.attachment.sizeBytes / 1024).toFixed(1)} KB`)
const isImage = computed(() => props.attachment.contentType.startsWith('image/'))
</script>

<template>
  <article class="attachment-preview" data-testid="attachment-preview">
    <img
      v-if="isImage"
      class="attachment-preview-image"
      data-testid="attachment-preview-image"
      :src="attachment.previewUrl"
      :alt="`Preview ${attachment.filename}`"
    >
    <div class="attachment-preview-meta">
      <strong>{{ attachment.filename }}</strong>
      <span>{{ attachment.contentType }} · {{ sizeLabel }}</span>
    </div>
    <button
      v-if="removable"
      type="button"
      data-testid="attachment-clear"
      aria-label="Remove staged attachment"
      @click="emit('remove')"
    >
      Remove
    </button>
  </article>
</template>
