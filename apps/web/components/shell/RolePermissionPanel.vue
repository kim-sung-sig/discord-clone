<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <aside
    class="role-permission-panel"
    data-testid="role-permission-panel"
    aria-label="Role permissions"
  >
    <header class="role-panel-header">
      <p>Admin preview</p>
      <h2>Role permissions</h2>
    </header>

    <section
      v-if="shell.adminPermissionDiff"
      class="role-panel-section admin-console-section"
      aria-labelledby="permission-diff-title"
    >
      <h3 id="permission-diff-title">Permission diff</h3>
      <article class="permission-diff" data-testid="permission-diff">
        <strong>{{ shell.adminPermissionDiff.roleName }}</strong>
        <span>{{ shell.adminPermissionDiff.permission }}</span>
        <small>Before {{ shell.adminPermissionDiff.beforeLabel }}</small>
        <small>After {{ shell.adminPermissionDiff.afterLabel }}</small>
      </article>
      <button
        class="apply-draft-button"
        type="button"
        data-testid="apply-permission-draft"
        @click="shell.applyPermissionDraft()"
      >
        Apply change
      </button>
    </section>

    <section class="role-panel-section" aria-labelledby="preview-role-title">
      <h3 id="preview-role-title">Preview as role</h3>
      <article class="preview-as-role" data-testid="preview-as-role">
        <strong>Preview as {{ shell.previewAsRole.roleName }}</strong>
        <small>Allowed {{ shell.previewAsRole.allowed.join(', ') || 'none' }}</small>
        <small>Denied {{ shell.previewAsRole.denied.join(', ') || 'none' }}</small>
      </article>
    </section>

    <section class="role-panel-section" aria-labelledby="role-list-title">
      <h3 id="role-list-title">Roles</h3>
      <article
        v-for="role in shell.roles"
        :key="role.id"
        class="role-card"
        :data-testid="role.id"
      >
        <strong>{{ role.name }}</strong>
        <div class="permission-chips" aria-label="Permissions">
          <span
            v-for="permission in role.permissions"
            :key="permission"
            class="permission-chip"
          >
            {{ permission }}
          </span>
        </div>
      </article>
    </section>

    <section class="role-panel-section" aria-labelledby="member-role-title">
      <h3 id="member-role-title">Member roles</h3>
      <p
        v-for="member in shell.memberRoleSummaries"
        :key="member.name"
        class="assignment-row"
        :data-testid="`member-${member.name}-roles`"
      >
        <strong>{{ member.name }}</strong>
        <span>{{ member.roleNames.join(', ') || '@everyone' }}</span>
      </p>
    </section>

    <section class="role-panel-section" aria-labelledby="overwrite-title">
      <h3 id="overwrite-title">Active channel overwrites</h3>
      <article
        v-for="overwrite in shell.activeChannelOverwriteSummaries"
        :key="overwrite.channelId + overwrite.roleId"
        class="overwrite-card"
        data-testid="active-channel-overwrite"
      >
        <strong>{{ overwrite.channelLabel }}</strong>
        <p>{{ overwrite.roleName }}</p>
        <small>Allow {{ overwrite.allow.join(', ') || 'none' }}</small>
        <small>Deny {{ overwrite.deny.join(', ') || 'none' }}</small>
      </article>
    </section>

    <section class="role-panel-section" aria-labelledby="privileged-audit-title">
      <h3 id="privileged-audit-title">Privileged audit</h3>
      <article
        v-for="entry in shell.privilegedAuditLogs"
        :key="entry.id"
        class="privileged-audit-entry"
        data-testid="privileged-audit"
      >
        <strong>{{ entry.action }}</strong>
        <span>{{ entry.detail }}</span>
      </article>
    </section>
  </aside>
</template>
