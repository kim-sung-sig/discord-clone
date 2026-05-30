import { createRequire } from 'node:module'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const require = createRequire(import.meta.url)
const livekitClientPath = require.resolve('livekit-client')
const backendBaseUrl = process.env.REAL_BACKEND_BASE_URL ?? 'http://127.0.0.1:8080'
const liveKitUrl = process.env.LIVEKIT_URL ?? 'ws://127.0.0.1:7880'
const liveKitPageUrl = liveKitUrl.replace(/^ws/, 'http')
const runLiveKitMediaSmoke = process.env.LIVEKIT_MEDIA_SMOKE === '1'

test.skip(!runLiveKitMediaSmoke, 'Set LIVEKIT_MEDIA_SMOKE=1 with a media-livekit backend and LiveKit server')

test('exchanges a synthetic LiveKit video track between two authorized voice participants', async ({ browser, request }) => {
  const stamp = Date.now()
  const fixtureId = String(stamp).slice(-10)
  const owner = await signup(request, `livekitowner${fixtureId}`)
  const member = await signup(request, `livekitmember${fixtureId}`)
  const guildId = await createGuild(request, owner)
  const channelId = await createVoiceChannel(request, owner, guildId)
  await addMember(request, owner, guildId, member.userId)

  const ownerJoin = await joinVoice(request, owner, channelId)
  const memberJoin = await joinVoice(request, member, channelId)

  expect(ownerJoin.token.provider).toBe('LIVEKIT')
  expect(memberJoin.token.provider).toBe('LIVEKIT')
  expect(ownerJoin.token.room).toBe(memberJoin.token.room)
  expect(ownerJoin.token.token).not.toBe(memberJoin.token.token)
  doesNotContain(JSON.stringify([ownerJoin.token, memberJoin.token]), process.env.DISCORD_MEDIA_LIVEKIT_API_SECRET)
  doesNotContain(JSON.stringify([ownerJoin.token, memberJoin.token]), process.env.LIVEKIT_API_SECRET)

  const ownerPage = await browser.newPage()
  const memberPage = await browser.newPage()
  try {
    const ownerConnection = await connectParticipant(ownerPage, ownerJoin.token.token, 'owner')
    const memberConnection = await connectParticipant(memberPage, memberJoin.token.token, 'member')

    expect(ownerConnection.roomName).toBe(ownerJoin.token.room)
    expect(memberConnection.roomName).toBe(ownerJoin.token.room)
    expect(ownerConnection.identity).toBe(ownerJoin.token.participant)
    expect(memberConnection.identity).toBe(memberJoin.token.participant)

    await expect.poll(() => hasSubscribedVideoFrom(memberPage, ownerJoin.token.participant), {
      message: 'member should subscribe to owner synthetic video track',
      timeout: 15_000
    }).toBe(true)
    await expect.poll(() => hasSubscribedVideoFrom(ownerPage, memberJoin.token.participant), {
      message: 'owner should subscribe to member synthetic video track',
      timeout: 15_000
    }).toBe(true)
  } finally {
    await disconnectParticipant(ownerPage)
    await disconnectParticipant(memberPage)
    await ownerPage.close()
    await memberPage.close()
  }
})

async function connectParticipant(page: Page, token: string, label: string) {
  await page.goto(liveKitPageUrl)
  await page.addScriptTag({ path: livekitClientPath })
  return page.evaluate(async ({ liveKitUrl, token, label }) => {
    const client = window.LivekitClient
    const room = new client.Room({
      adaptiveStream: false,
      dynacast: false
    })
    const events: Array<Record<string, string>> = []
    const tracks: MediaStreamTrack[] = []
    window.__livekitSmoke = { room, events, tracks }
    room.on(client.RoomEvent.TrackSubscribed, (track, _publication, participant) => {
      events.push({
        type: 'TrackSubscribed',
        kind: track.kind,
        participantIdentity: participant.identity
      })
    })
    await room.connect(liveKitUrl, token, { autoSubscribe: true })

    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 64
    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('canvas context unavailable')
    }
    context.fillStyle = label === 'owner' ? '#0ea5e9' : '#22c55e'
    context.fillRect(0, 0, canvas.width, canvas.height)

    const stream = canvas.captureStream(5)
    const [videoTrack] = stream.getVideoTracks()
    if (!videoTrack) {
      throw new Error('synthetic video track unavailable')
    }
    tracks.push(videoTrack)
    await room.localParticipant.publishTrack(videoTrack, { name: `${label}-synthetic-video` })

    return {
      identity: room.localParticipant.identity,
      roomName: room.name
    }
  }, { liveKitUrl, token, label })
}

async function hasSubscribedVideoFrom(page: Page, participantIdentity: string): Promise<boolean> {
  return page.evaluate((participantIdentity) => {
    return window.__livekitSmoke.events.some((event) =>
      event.type === 'TrackSubscribed' &&
      event.kind === 'video' &&
      event.participantIdentity === participantIdentity
    )
  }, participantIdentity)
}

async function disconnectParticipant(page: Page): Promise<void> {
  await page.evaluate(() => {
    if (!window.__livekitSmoke) {
      return
    }
    for (const track of window.__livekitSmoke.tracks) {
      track.stop()
    }
    window.__livekitSmoke.room.disconnect()
  }).catch(() => undefined)
}

async function signup(request: APIRequestContext, username: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/auth/signup`, {
    data: {
      email: `${username}@example.com`,
      username,
      displayName: username,
      password: 'correct horse battery staple'
    }
  })
  expect(response.status()).toBe(201)
  const body = await response.json()
  return {
    accessToken: body.accessToken,
    userId: body.user.id
  }
}

async function createGuild(request: APIRequestContext, owner: AuthSession): Promise<string> {
  const response = await request.post(`${backendBaseUrl}/api/guilds`, {
    headers: authHeaders(owner),
    data: { name: 'LiveKit Smoke Guild' }
  })
  expect(response.status()).toBe(201)
  return (await response.json()).id
}

async function createVoiceChannel(request: APIRequestContext, owner: AuthSession, guildId: string): Promise<string> {
  const response = await request.post(`${backendBaseUrl}/api/guilds/${guildId}/channels`, {
    headers: authHeaders(owner),
    data: {
      name: 'livekit-smoke',
      type: 'GUILD_VOICE',
      parentId: null
    }
  })
  expect(response.status()).toBe(201)
  return (await response.json()).id
}

async function addMember(
  request: APIRequestContext,
  owner: AuthSession,
  guildId: string,
  memberId: string
): Promise<void> {
  const response = await request.put(`${backendBaseUrl}/api/guilds/${guildId}/members/${memberId}`, {
    headers: authHeaders(owner)
  })
  expect(response.ok()).toBeTruthy()
}

async function joinVoice(request: APIRequestContext, session: AuthSession, channelId: string): Promise<VoiceJoinResponse> {
  const response = await request.post(`${backendBaseUrl}/api/voice/channels/${channelId}/join`, {
    headers: authHeaders(session)
  })
  expect(response.status()).toBe(200)
  return response.json()
}

function authHeaders(session: AuthSession): Record<string, string> {
  return {
    Authorization: `Bearer ${session.accessToken}`
  }
}

function doesNotContain(value: string, forbidden: string | undefined): void {
  if (forbidden) {
    expect(value).not.toContain(forbidden)
  }
}

interface AuthSession {
  accessToken: string
  userId: string
}

interface VoiceJoinResponse {
  token: {
    provider: string
    room: string
    participant: string
    token: string
  }
}

declare global {
  interface Window {
    LivekitClient: {
      Room: new (options: Record<string, unknown>) => LiveKitRoom
      RoomEvent: {
        TrackSubscribed: string
      }
    }
    __livekitSmoke: {
      room: LiveKitRoom
      events: Array<Record<string, string>>
      tracks: MediaStreamTrack[]
    }
  }
}

interface LiveKitRoom {
  name: string
  localParticipant: {
    identity: string
    publishTrack(track: MediaStreamTrack, options: Record<string, unknown>): Promise<unknown>
  }
  on(event: string, callback: (track: { kind: string }, publication: unknown, participant: { identity: string }) => void): void
  connect(url: string, token: string, options: Record<string, unknown>): Promise<void>
  disconnect(): void
}
