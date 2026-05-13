import ChannelSidebar from './ChannelSidebar.vue'

const meta = {
  title: 'Shell/ChannelSidebar',
  component: ChannelSidebar,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    activeChannelId: 'channel-general',
    channelGroups: 'seeded-shell-store'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
