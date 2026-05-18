<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const dialog = ref<HTMLElement | null>(null)

onMounted(() => {
  dialog.value?.focus()
})

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

const trapFocus = (event: KeyboardEvent) => {
  if (event.key !== 'Tab' || !dialog.value) {
    return
  }

  const focusable = Array.from(dialog.value.querySelectorAll<HTMLElement>(focusableSelector))
  if (focusable.length === 0) {
    event.preventDefault()
    dialog.value.focus()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement

  if (active && !dialog.value.contains(active)) {
    event.preventDefault()
    first.focus()
  } else if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <aside
    ref="dialog"
    class="invite-modal"
    data-testid="invite-modal"
    role="dialog"
    aria-modal="true"
    aria-labelledby="invite-modal-title"
    tabindex="-1"
    @keydown="trapFocus"
  >
    <p class="invite-eyebrow">Invite preview</p>
    <h2 id="invite-modal-title">Join {{ shell.invitePreviewSummary.guildName }}</h2>

    <section class="invite-preview" data-testid="invite-preview">
      <strong>{{ shell.invitePreviewSummary.code }}</strong>
      <span>Previewing {{ shell.invitePreviewSummary.channelLabel }}</span>
    </section>

    <dl class="invite-meta" aria-label="Invite limits">
      <div data-testid="invite-expiry">
        <dt>Expiry</dt>
        <dd>Expires in {{ shell.invitePreviewSummary.expiresIn }}</dd>
      </div>
      <div data-testid="invite-max-uses">
        <dt>Max uses</dt>
        <dd>{{ shell.invitePreviewSummary.usesRemaining }} uses remaining</dd>
      </div>
    </dl>

    <section class="invite-role-grants" data-testid="invite-role-grants" aria-labelledby="invite-role-title">
      <h3 id="invite-role-title">Role grants</h3>
      <span
        v-for="roleName in shell.invitePreviewSummary.roleNames"
        :key="roleName"
        class="invite-role-chip"
      >
        {{ roleName }}
      </span>
    </section>

    <button class="invite-accept" type="button" data-testid="invite-accept">
      Accept invite
    </button>
  </aside>
</template>
