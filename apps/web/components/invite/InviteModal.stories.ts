import InviteModal from './InviteModal.vue'

const meta = {
  title: 'Invite/InviteModal',
  component: InviteModal,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    code: 'T06-SHELL',
    guildName: 'Discord Clone',
    channelLabel: '# general'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
