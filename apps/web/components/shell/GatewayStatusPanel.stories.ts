import GatewayStatusPanel from './GatewayStatusPanel.vue'

const meta = {
  title: 'Shell/GatewayStatusPanel',
  component: GatewayStatusPanel,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    status: 'READY',
    lastSequence: 42
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
