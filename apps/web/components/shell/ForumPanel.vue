<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ThreadList from './ThreadList.vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const isHydrated = ref(false)

onMounted(() => {
  isHydrated.value = true
})

const createPostWithoutTag = () => {
  shell.createForumPost('Release plan', [])
}

const createReleasePost = () => {
  shell.createForumPost('Release plan', ['release'])
}
</script>

<template>
  <aside class="forum-panel" data-testid="forum-panel" aria-label="Forum">
    <header class="forum-panel-header">
      <p>Forum</p>
      <h2>Thread board</h2>
    </header>

    <section v-if="shell.activeForum" class="forum-guidelines-card">
      <p data-testid="forum-guidelines">{{ shell.activeForum.guidelines }}</p>
      <div class="forum-tags" aria-label="Forum tags">
        <span
          v-for="tag in shell.activeForum.tags"
          :key="tag.id"
          class="forum-tag"
          :data-testid="`forum-tag-${tag.id}`"
        >
          {{ tag.label }}{{ tag.required ? ' required' : '' }}
        </span>
      </div>
    </section>

    <div class="forum-actions">
      <button
        type="button"
        data-testid="create-forum-post-without-tag"
        :disabled="!isHydrated"
        @click="createPostWithoutTag"
      >
        Create without tag
      </button>
      <button
        type="button"
        data-testid="create-forum-post-release"
        :disabled="!isHydrated"
        @click="createReleasePost"
      >
        Create release post
      </button>
    </div>
    <p class="forum-post-error" data-testid="forum-post-error" role="status">
      {{ shell.forum.postError }}
    </p>

    <ThreadList />

    <section v-if="shell.activeThread" class="thread-detail" data-testid="thread-detail">
      <p>Selected thread</p>
      <h3>{{ shell.activeThread.title }}</h3>
      <small>Auto archive {{ shell.activeThread.autoArchiveAt }}</small>
      <div class="thread-actions">
        <button
          type="button"
          :data-testid="`thread-write-${shell.activeThread.id}`"
          :disabled="!isHydrated"
          @click="shell.writeThreadMessage(shell.activeThread.id)"
        >
          Write skeleton message
        </button>
        <button
          type="button"
          :data-testid="`reopen-thread-${shell.activeThread.id}`"
          :disabled="!isHydrated"
          @click="shell.reopenThread(shell.activeThread.id)"
        >
          Reopen
        </button>
      </div>
      <p data-testid="thread-write-receipt" role="status">
        {{ shell.activeThread.writeReceipt }}
      </p>
    </section>
  </aside>
</template>
