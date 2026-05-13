import UserPanel from './UserPanel.vue'

const meta = {
  title: 'Shell/UserPanel',
  component: UserPanel,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    currentUser: 'vibe-coder',
    voiceState: 'Voice connected'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
