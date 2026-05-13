<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const isHydrated = ref(false)

const activeGroup = computed(() => shell.activeGroupDm)
const canAddQaScout = computed(
  () => isHydrated.value && Boolean(activeGroup.value) && !activeGroup.value?.memberIds.includes('qa-scout')
)

const addQaScout = () => {
  if (activeGroup.value) {
    shell.addGroupDmMember(activeGroup.value.id, 'qa-scout')
  }
}

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <aside class="dm-sidebar" data-testid="dm-sidebar" aria-label="Direct messages">
    <header class="dm-header">
      <p>Social</p>
      <h2>Direct messages</h2>
    </header>

    <section class="dm-section" aria-labelledby="direct-message-title">
      <h3 id="direct-message-title">Friends</h3>
      <button
        v-for="dm in shell.socialDirectSummaries"
        :key="dm.id"
        class="dm-row"
        :class="{ 'dm-row-blocked': dm.friend?.status === 'BLOCKED' }"
        type="button"
        :data-testid="`dm-friend-${dm.friend?.name}`"
        :aria-current="shell.social.activeSelection.type === 'DIRECT' && shell.social.activeSelection.id === dm.id ? 'page' : undefined"
        :disabled="!isHydrated || dm.friend?.status === 'BLOCKED'"
        @click="shell.selectDirectDm(dm.id)"
      >
        <span>{{ dm.friend?.name }}</span>
        <strong v-if="dm.friend?.status === 'BLOCKED'" :data-testid="`dm-blocked-${dm.friend.name}`">
          Blocked
        </strong>
        <strong v-else>Friend</strong>
        <small v-if="dm.unreadCount">{{ dm.unreadCount }} unread</small>
      </button>
    </section>

    <section class="dm-section" aria-labelledby="group-message-title">
      <h3 id="group-message-title">Group DMs</h3>
      <button
        v-for="groupDm in shell.social.groupDms"
        :key="groupDm.id"
        class="dm-row group-dm-row"
        type="button"
        :data-testid="groupDm.id"
        :aria-current="shell.social.activeSelection.type === 'GROUP' && shell.social.activeSelection.id === groupDm.id ? 'page' : undefined"
        :disabled="!isHydrated"
        @click="shell.selectGroupDm(groupDm.id)"
      >
        <span>{{ groupDm.name }}</span>
        <small>{{ groupDm.memberIds.length }} members</small>
      </button>
    </section>

    <section class="dm-active-card" data-testid="active-dm-summary" aria-label="Active DM">
      <p>Active DM</p>
      <h3>{{ shell.activeSocialLabel }}</h3>
    </section>

    <section
      v-if="activeGroup"
      class="dm-section group-members"
      data-testid="group-dm-members"
      aria-labelledby="group-members-title"
    >
      <div class="dm-section-heading">
        <h3 id="group-members-title">Members</h3>
        <button
          type="button"
          data-testid="add-group-member"
          :disabled="!canAddQaScout"
          @click="addQaScout"
        >
          Add qa-scout
        </button>
      </div>

      <article
        v-for="memberId in activeGroup.memberIds"
        :key="memberId"
        class="group-member"
        :data-testid="`group-dm-member-${memberId}`"
      >
        <span>{{ memberId }}</span>
        <button
          v-if="memberId !== activeGroup.ownerId"
          type="button"
          :data-testid="`remove-group-member-${memberId}`"
          :disabled="!isHydrated"
          @click="shell.removeGroupDmMember(activeGroup.id, memberId)"
        >
          Remove
        </button>
        <small v-else>Owner</small>
      </article>
    </section>

    <section
      v-if="activeGroup"
      class="group-call-skeleton"
      data-testid="group-call-skeleton"
      aria-label="Group call"
    >
      <div>
        <p>Group call</p>
        <strong data-testid="group-call-status">
          {{ activeGroup.call.active ? 'Call active' : 'Call idle' }}
        </strong>
      </div>
      <button
        type="button"
        data-testid="group-call-toggle"
        :disabled="!isHydrated"
        @click="shell.toggleActiveGroupCall"
      >
        {{ activeGroup.call.active ? 'End call' : 'Start call' }}
      </button>
      <p data-testid="group-call-participants">
        Participants:
        {{ activeGroup.call.participants.length ? activeGroup.call.participants.join(', ') : 'none' }}
      </p>
    </section>
  </aside>
</template>
