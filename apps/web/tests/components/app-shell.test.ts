import { mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import App from '../../app.vue'

describe('Discord app shell', () => {
  it('renders guild, grouped channels, active channel, member sidebar, and user panel', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="server-rail"]').text()).toContain('Discord Clone')
    expect(wrapper.get('[data-testid="guild-name"]').text()).toContain('Discord Clone')
    expect(wrapper.get('[data-testid="channel-general"]').text()).toContain('#')
    expect(wrapper.get('[data-testid="channel-general"]').text()).toContain('general')
    expect(wrapper.get('[data-testid="channel-war-room"]').text()).toContain('Voice')
    expect(wrapper.get('[data-testid="channel-war-room"]').text()).toContain('war-room')
    expect(wrapper.get('[data-testid="active-channel"]').text()).toContain('# general')
    expect(wrapper.get('[data-channel-id="channel-general"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Welcome to the guild')
    expect(wrapper.get('[data-testid="message-pinned-label"]').text()).toContain('Pinned')
    expect(wrapper.get('[data-testid="message-edited-marker"]').text()).toContain('edited')
    expect(wrapper.get('[data-testid="message-tombstone"]').text()).toContain('message deleted')
    expect(wrapper.get('[data-testid="mention-chip-cto-bot"]').text()).toContain('@cto-bot')
    expect(wrapper.get('[data-testid="message-input"]').attributes('placeholder')).toContain('Message # general')
    expect(wrapper.get('[data-testid="message-list"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="chat-viewport"]').find('[data-testid="message-composer"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('Members')
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('online')
    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('vibe-coder')
  })

  it('sends composed messages from the active channel composer', async () => {
    const wrapper = await mountSuspended(App)
    const input = wrapper.get('[data-testid="message-input"]')

    const beforeEmptySubmit = wrapper.findAll('[data-testid="message-card"]').length
    await input.setValue('   ')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')
    expect(wrapper.findAll('[data-testid="message-card"]')).toHaveLength(beforeEmptySubmit)

    await input.setValue('Shipping T04 from the composer')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Shipping T04 from the composer')
    expect((input.element as HTMLInputElement).value).toBe('')
  })

  it('extracts user mentions without matching emails and scopes sends to the active channel', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')
    await wrapper.get('[data-testid="message-input"]').setValue('Ping dev@example.com @cto-bot @CTO-BOT')
    await wrapper.get('[data-testid="message-composer"]').trigger('submit')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Ping dev@example.com @cto-bot @CTO-BOT')
    expect(wrapper.findAll('[data-testid="mention-chip-cto-bot"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="mention-chip-example"]').exists()).toBe(false)

    await wrapper.get('[data-testid="channel-general"]').trigger('click')

    expect(wrapper.get('[data-testid="chat-viewport"]').text()).not.toContain('Ping dev@example.com @cto-bot @CTO-BOT')
  })

  it('renders role permissions, member assignments, and active channel overwrites', async () => {
    const wrapper = await mountSuspended(App)

    expect(wrapper.get('[data-testid="role-permission-panel"]').text()).toContain('Role permissions')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('MANAGE_MESSAGES')
    expect(wrapper.get('[data-testid="role-moderator"]').text()).toContain('VIEW_CHANNEL')
    expect(wrapper.get('[data-testid="member-vibe-coder-roles"]').text()).toContain('vibe-coder')
    expect(wrapper.get('[data-testid="member-vibe-coder-roles"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('# general')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Moderator')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Allow SEND_MESSAGES')
    expect(wrapper.get('[data-testid="active-channel-overwrite"]').text()).toContain('Deny MANAGE_CHANNELS')
    expect(wrapper.get('[data-testid="workspace"]').find('[data-testid="role-permission-panel"]').exists()).toBe(true)
  })

  it('renders the invite preview modal with limits, role grants, and accept CTA inside the workspace', async () => {
    const wrapper = await mountSuspended(App)
    const inviteModal = wrapper.get('[data-testid="invite-modal"]')

    expect(wrapper.get('[data-testid="workspace"]').find('[data-testid="invite-modal"]').exists()).toBe(true)
    expect(inviteModal.attributes('role')).toBe('dialog')
    expect(inviteModal.attributes('aria-modal')).toBe('true')
    expect(inviteModal.attributes('tabindex')).toBe('-1')
    expect(inviteModal.text()).toContain('Join Discord Clone')
    expect(inviteModal.get('[data-testid="invite-preview"]').text()).toContain('Previewing # general')
    expect(inviteModal.get('[data-testid="invite-expiry"]').text()).toContain('Expires in 7 days')
    expect(inviteModal.get('[data-testid="invite-max-uses"]').text()).toContain('12 uses remaining')
    expect(inviteModal.get('[data-testid="invite-role-grants"]').text()).toContain('Role grants')
    expect(inviteModal.get('[data-testid="invite-role-grants"]').text()).toContain('Moderator')
    expect(inviteModal.get('[data-testid="invite-accept"]').text()).toContain('Accept invite')
  })

  it('selects channels with accessible channel buttons and shows channel-specific messages', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')

    expect(wrapper.get('[data-testid="active-channel"]').text()).toContain('# architecture')
    expect(wrapper.get('[data-channel-id="channel-architecture"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Architecture notes belong in this channel')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).not.toContain('Welcome to the guild')
  })
})
