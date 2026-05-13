import RolePermissionPanel from './RolePermissionPanel.vue'

const meta = {
  title: 'Shell/RolePermissionPanel',
  component: RolePermissionPanel,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    roles: 'seeded-shell-store',
    activeChannelOverwrites: 'seeded-shell-store'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
