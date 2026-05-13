import DmSidebar from './DmSidebar.vue'

const meta = {
  title: 'Social/DmSidebar',
  component: DmSidebar,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    activeSelection: 'group-dm-t07-strike-team',
    socialState: 'seeded-shell-store'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
