import ChatViewport from './ChatViewport.vue'

const meta = {
  title: 'Shell/ChatViewport',
  component: ChatViewport,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    activeChannel: '# general',
    messages: 'seeded-shell-store'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
