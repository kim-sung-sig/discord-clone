import ServerRail from './ServerRail.vue'

const meta = {
  title: 'Shell/ServerRail',
  component: ServerRail,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    scenario: 'seeded-guild'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
