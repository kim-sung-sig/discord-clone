<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const dialog = ref<HTMLElement | null>(null)

onMounted(() => {
  dialog.value?.focus()
})
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
