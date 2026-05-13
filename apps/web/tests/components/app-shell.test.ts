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
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('Members')
    expect(wrapper.get('[data-testid="member-sidebar"]').text()).toContain('online')
    expect(wrapper.get('[data-testid="user-panel"]').text()).toContain('vibe-coder')
  })

  it('selects channels with accessible channel buttons and shows channel-specific messages', async () => {
    const wrapper = await mountSuspended(App)

    await wrapper.get('[data-testid="channel-architecture"]').trigger('click')

    expect(wrapper.get('[data-testid="active-channel"]').text()).toContain('# architecture')
    expect(wrapper.get('[data-channel-id="channel-architecture"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="chat-viewport"]').text()).toContain('Architecture notes belong in this channel')
  })
})
