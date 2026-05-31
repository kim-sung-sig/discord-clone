<script setup lang="ts">
defineProps<{
  tokenInput: string
  saved: boolean
  expiresAt: string
}>()

const emit = defineEmits<{
  'update:tokenInput': [value: string]
  issue: []
  clear: []
}>()

const updateTokenInput = (event: Event) => {
  emit('update:tokenInput', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <form
    class="security-operator-token"
    data-testid="operator-token-form"
    aria-label="Operator token"
    @submit.prevent="emit('issue')"
  >
    <label for="operator-token-input">Operator token</label>
    <div>
      <input
        id="operator-token-input"
        :value="tokenInput"
        data-testid="operator-token-input"
        type="password"
        autocomplete="off"
        @input="updateTokenInput"
      >
      <button type="submit">Issue</button>
      <button v-if="saved" type="button" @click="emit('clear')">Clear</button>
    </div>
    <small v-if="expiresAt" data-testid="operator-token-expiry">
      Expires {{ expiresAt }}
    </small>
  </form>
</template>
