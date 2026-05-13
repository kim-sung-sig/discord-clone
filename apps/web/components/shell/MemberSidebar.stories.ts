import MemberSidebar from './MemberSidebar.vue'

const meta = {
  title: 'Shell/MemberSidebar',
  component: MemberSidebar,
  tags: ['framework-light'],
  parameters: {
    layout: 'fullscreen'
  },
  args: {
    members: 'seeded-shell-store'
  }
}

export default meta

export const Default = {
  name: 'Default',
  args: {
    ...meta.args
  }
}
