import { describe, expect, it } from 'vitest'
import channelSidebarStories from '../../components/shell/ChannelSidebar.stories'
import chatViewportStories from '../../components/shell/ChatViewport.stories'
import gatewayStatusPanelStories from '../../components/shell/GatewayStatusPanel.stories'
import inviteModalStories from '../../components/invite/InviteModal.stories'
import dmSidebarStories from '../../components/social/DmSidebar.stories'
import memberSidebarStories from '../../components/shell/MemberSidebar.stories'
import rolePermissionPanelStories from '../../components/shell/RolePermissionPanel.stories'
import serverRailStories from '../../components/shell/ServerRail.stories'
import userPanelStories from '../../components/shell/UserPanel.stories'

const stories = [
  serverRailStories,
  channelSidebarStories,
  chatViewportStories,
  memberSidebarStories,
  userPanelStories,
  rolePermissionPanelStories,
  gatewayStatusPanelStories,
  inviteModalStories,
  dmSidebarStories
]

describe('Discord shell story index', () => {
  it('publishes framework-light stories for every shell region, invite modal, and social surface', () => {
    expect(stories).toHaveLength(9)

    for (const storyModule of stories) {
      expect(storyModule.title).toMatch(/^(Shell|Invite|Social)\//)
      expect(storyModule.component).toBeTruthy()
      expect(storyModule.tags).toContain('framework-light')
      expect(storyModule.parameters?.layout).toBeTruthy()
    }
  })
})
