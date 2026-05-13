# Discord 기능 분석 보고서

작성일: 2026-05-13  
대상: Spring Boot + Nuxt 기반 Discord 클론  
리서치 기준: Discord 공식 Help Center, Discord Developer Docs, 2026년 5월 기준 공개 문서

## 1. 요약

Discord는 단순 채팅 앱이 아니라 서버(guild), 채널, 권한, 실시간 메시징, 음성/영상, 커뮤니티 운영, 표현 도구, 초대/온보딩, 수익화, 안전/모더레이션이 결합된 실시간 커뮤니케이션 플랫폼이다. 클론 구현은 기능을 한 번에 모두 만들기보다, 아래 4개 레이어로 나누어 진행해야 한다.

1. Core Communication: 인증, 사용자, 서버, 멤버십, 친구, DM, 그룹 DM, 텍스트 채널, 메시지, 첨부, 멘션, 반응
2. Realtime & Media: WebSocket Gateway, presence, typing, voice state, WebRTC/SFU voice, screen share, soundboard
3. Community Operations: 역할/권한, 초대, 온보딩, 포럼, 스레드, 이벤트/스테이지, AutoMod, 감사 로그
4. Growth & Premium: 커스텀 이모지/스티커, 프로필 꾸미기, 서버 샵, Nitro 유사 구독, Quests/프로모션

이 프로젝트의 첫 구현 목표는 Discord 전체 복제가 아니라, Discord의 핵심 UX와 확장 가능한 도메인 모델을 갖춘 엔터프라이즈급 클론 플랫폼이다.

## 2. 공식 기능 기준

### 2.1 서버와 커뮤니티

Discord Developer Docs는 guild를 "사용자와 채널의 격리된 컬렉션"으로 정의하며, UI에서는 server라고 부른다. 서버는 이름, 아이콘, 소유자, 기능 플래그, 멤버, 역할, 채널, 초대, 이모지/스티커, 권한 정책을 가진다.

구현 명세:

- 서버 생성, 수정, 삭제
- 서버 소유자와 관리자 권한
- 서버 멤버 가입/탈퇴/강퇴/밴
- 서버 프로필: 아이콘, 배너, 설명, 공개 여부
- 커뮤니티 서버 플래그
- 멤버 수, 온라인 수 집계
- 서버 템플릿: 카테고리, 채널, 역할, 권한 복제
- 서버 검색/디렉터리: Discord Server Discovery는 실험 종료 상태로 문서화되어 있으므로, 클론에서는 "Community Directory"로 별도 설계한다.

주요 출처:

- Discord Guild Resource: https://docs.discord.com/developers/resources/guild
- Community Servers: https://docs.discord.com/developers/communities/overview
- Server Templates: https://support.discord.com/hc/en-us/articles/360041033511-Server-Templates
- Server Discovery: https://support.discord.com/hc/en-us/articles/360023968311-Server-Discovery

### 2.2 채널 모델

Discord의 Channel Resource는 guild 또는 DM channel을 표현한다. 채널은 id, type, guild_id, position, permission_overwrites, name, topic 등을 가진다. 텍스트, 음성, DM, 그룹 DM, 카테고리, 공지, 스테이지, 포럼/미디어, 스레드까지 확장된다.

구현 명세:

- 채널 타입
  - CATEGORY
  - GUILD_TEXT
  - GUILD_VOICE
  - GUILD_STAGE
  - GUILD_FORUM
  - GUILD_ANNOUNCEMENT
  - DM
  - GROUP_DM
  - THREAD_PUBLIC
  - THREAD_PRIVATE
- 채널 정렬 position
- 카테고리 하위 채널
- 채널별 주제/topic
- 채널별 권한 overwrite
- 채널 복제
- 채널 아카이브/삭제

주요 출처:

- Channels Resource: https://docs.discord.com/developers/resources/channel
- Channel Permissions Settings: https://support.discord.com/hc/en-us/articles/10543994968087-Channel-Permissions-Settings-101

### 2.3 메시지와 채팅

Discord Message Resource는 메시지를 채널 내에 생성되는 객체로 정의한다. Gateway의 MESSAGE_CREATE, MESSAGE_UPDATE, MESSAGE_DELETE 이벤트와 REST API가 함께 사용된다. 메시지는 content, attachments, embeds, reactions, mentions, references, poll, call, snapshots 등을 포함한다.

구현 명세:

- 메시지 생성/수정/삭제
- soft delete와 hard delete 분리
- 메시지 조회 pagination: cursor 기반 before/after/around
- 메시지 검색: full-text index
- 메시지 첨부 파일
- 멘션: user, role, channel, everyone/here
- 답글/reply
- 메시지 고정/pin
- 링크 프리뷰
- typing indicator
- 읽음 상태/read marker
- 메시지 편집 이력
- 메시지 신고/report
- 음성 메시지: 후순위
- poll: 질문, 최대 10개 답변, 투표/취소, AutoMod 필터 적용

주요 출처:

- Message Resource: https://docs.discord.com/developers/resources/message
- Gateway Events: https://docs.discord.com/developers/events/gateway-events
- Polls FAQ: https://support.discord.com/hc/en-us/articles/22163184112407-Polls-FAQ

### 2.4 실시간 Gateway

Discord Gateway는 WebSocket 기반 실시간 통신이다. Gateway는 서버/채널/역할/메시지/음성 상태 변경을 이벤트로 전달하며, 연결 생명주기에는 Hello, Heartbeat, Identify, Ready, Resume이 포함된다. 큰 앱은 상태 캐시를 운영해야 한다.

구현 명세:

- WebSocket Gateway endpoint
- auth handshake
- heartbeat ping/pong
- resume token/session id
- event sequence number
- shard/channel partitioning
- subscription model: user server membership 기준으로 이벤트 라우팅
- event envelope:

```json
{
  "op": 0,
  "t": "MESSAGE_CREATE",
  "s": 42,
  "d": {}
}
```

- 주요 이벤트:
  - READY
  - GUILD_CREATE/UPDATE/DELETE
  - CHANNEL_CREATE/UPDATE/DELETE
  - THREAD_CREATE/UPDATE/DELETE
  - MESSAGE_CREATE/UPDATE/DELETE
  - MESSAGE_REACTION_ADD/REMOVE
  - TYPING_START
  - PRESENCE_UPDATE
  - VOICE_STATE_UPDATE
  - INVITE_CREATE/DELETE
  - GUILD_MEMBER_ADD/UPDATE/REMOVE
  - AUTO_MODERATION_ACTION_EXECUTION

주요 출처:

- Gateway: https://docs.discord.com/developers/events/gateway
- Gateway Events: https://docs.discord.com/developers/events/gateway-events
- Overview of Events: https://docs.discord.com/developers/events/overview

### 2.5 역할과 권한

Discord 권한은 guild-level role permission과 channel-level permission overwrite를 조합해 계산한다. Developer Docs는 권한을 bitwise integer로 저장한다고 설명한다. Support 문서는 역할 계층과 채널 권한 관리가 서버 보안의 핵심이라고 설명한다.

구현 명세:

- 권한 bitset
- 역할 hierarchy position
- @everyone 기본 역할
- 사용자 멤버십에 다중 역할 부여
- 채널 permission overwrite
- allow/deny/neutral 3상태
- Administrator 권한은 모든 채널 제한 우회
- 자기보다 높은 역할의 사용자/역할은 관리 불가
- 권한 계산 엔진 단위 테스트 필수

초기 권한 enum:

- VIEW_CHANNEL
- SEND_MESSAGES
- MANAGE_MESSAGES
- MANAGE_CHANNELS
- MANAGE_ROLES
- MANAGE_SERVER
- KICK_MEMBERS
- BAN_MEMBERS
- CREATE_INVITE
- CONNECT
- SPEAK
- STREAM
- MUTE_MEMBERS
- DEAFEN_MEMBERS
- MOVE_MEMBERS
- MANAGE_EVENTS
- CREATE_EXPRESSIONS
- MANAGE_EXPRESSIONS
- USE_SOUNDBOARD
- ADMINISTRATOR

주요 출처:

- Permissions: https://docs.discord.com/developers/topics/permissions
- Discord Roles and Permissions: https://support.discord.com/hc/en-us/articles/214836687-Discord-Roles-and-Permissions

### 2.6 DM, 그룹 DM, 친구

Discord의 DM은 서버와 분리된 개인 대화이며, 그룹 DM은 서버 없이 소규모 그룹이 메시지, 음성 통화, 영상 통화, 고정 메시지, 멤버 관리를 할 수 있다.

구현 명세:

- 친구 요청: send/accept/reject/cancel/block
- 친구 상태: pending, accepted, blocked
- DM channel 자동 생성
- 그룹 DM 생성, 이름/아이콘 변경, 멤버 추가/제거, 나가기
- 그룹 DM voice/video call state
- privacy setting: 친구만 DM 허용, 공통 서버 멤버 DM 허용
- 차단된 사용자의 DM/멘션/친구 요청 제한

주요 출처:

- Group Chat and Calls: https://support.discord.com/hc/en-us/articles/223657667-Group-Chat-and-Calls
- Blocking & Privacy Settings: https://support.discord.com/hc/en-us/articles/217916488-Blocking-Privacy-Settings

### 2.7 음성, 영상, 화면 공유

Discord Voice docs는 Gateway와 유사한 voice connection이 별도 UDP 기반 데이터 전송을 사용한다고 설명한다. 실제 클론에서는 브라우저 기반 Nuxt를 기준으로 WebRTC가 필요하며, 다자 음성/화면 공유는 SFU가 필요하다.

구현 명세:

- voice channel join/leave
- voice state: mute, deaf, self_mute, self_deaf, speaking, stream, camera
- WebRTC signaling
- SFU: LiveKit 또는 mediasoup
- server-side voice room mapping
- screen share
- video call
- push-to-talk: 클라이언트 제어
- voice reconnect
- voice presence 이벤트
- recording/clip은 기본 범위 제외, 후순위

주요 출처:

- Discord Voice: https://docs.discord.com/developers/topics/voice-connections
- Group Chat and Calls: https://support.discord.com/hc/en-us/articles/223657667-Group-Chat-and-Calls
- Stage Channels FAQ: https://support.discord.com/hc/en-us/articles/1500005513722-Stage-Channels-FAQ

### 2.8 스테이지, 이벤트, 포럼, 스레드

Stage Channel은 Community server 전용 특수 음성 채널이다. 발표자, 모더레이터, 청중 역할을 나누며, 영상/화면 공유도 제한적으로 지원된다. Forum Channel은 주제별 post와 tag, inactive hide, list/gallery layout을 지원한다. Thread는 하나의 채널 안에서 여러 주제를 임시 독립 공간으로 분리한다.

구현 명세:

- Stage:
  - topic
  - speaker/audience/moderator state
  - request to speak
  - invite to speak
  - move to audience
  - silent join/leave
- Forum:
  - forum post = public thread + starter message
  - tags
  - guidelines
  - inactive archive
  - list/gallery layout flag
- Thread:
  - public/private
  - auto archive duration
  - join/leave
  - close/reopen/delete
  - parent channel permission inheritance

주요 출처:

- Stage Channels FAQ: https://support.discord.com/hc/en-us/articles/1500005513722-Stage-Channels-FAQ
- Forum Channels FAQ: https://support.discord.com/hc/en-us/articles/6208479917079-Forum-Channels-FAQ
- Threads FAQ: https://support.discord.com/hc/en-us/articles/4403205878423-Threads-FAQ

### 2.9 초대와 링크

Discord Invite는 guild/group DM에 사용자를 추가하는 code 객체다. 표준 초대는 만료 시간, 최대 사용 횟수, 임시 멤버십을 가진다. Community Invites는 역할 자동 부여와 대상 사용자 제한을 확장한다.

구현 명세:

- 초대 링크 생성
- code uniqueness
- max_age, max_uses, uses
- temporary membership
- channel-scoped invite
- invite pause
- invite delete
- invite preview: 서버 이름, 아이콘, 멤버 수, 온라인 수
- vanity URL: 후순위
- community invite:
  - role_ids 자동 부여
  - target user allowlist

주요 출처:

- Invites 101: https://support.discord.com/hc/en-us/articles/208866998-Invites-101
- Invite Resource: https://docs.discord.com/developers/resources/invite
- Community Invites: https://docs.discord.com/developers/communities/guides/community-invites

### 2.10 이모지, 스티커, 반응, 사운드보드

Discord 표현 기능은 메시지 반응, Super Reactions, 커스텀 이모지, 커스텀 스티커, 사운드보드로 구성된다. 이모지/스티커/사운드보드는 서버 권한과 Nitro/Boost 정책에 영향을 받는다.

구현 명세:

- custom emoji CRUD
- animated emoji metadata
- sticker CRUD
- sticker alt text/description
- reaction add/remove
- reaction list by emoji
- super reaction은 premium flag로 모델링
- soundboard sound CRUD
- soundboard play event: voice channel participants에게 broadcast
- expression 권한:
  - CREATE_EXPRESSIONS
  - MANAGE_EXPRESSIONS
  - USE_SOUNDBOARD

주요 출처:

- Emoji Resource: https://docs.discord.com/developers/resources/emoji
- Sticker Resource: https://docs.discord.com/developers/resources/sticker
- Reactions and Super Reactions FAQ: https://support.discord.com/hc/en-us/articles/12102061808663-Reactions-and-Super-Reactions-FAQ.%C2%A0
- Custom Stickers FAQ: https://support.discord.com/hc/en-us/articles/4403089981975
- Soundboard Resource: https://docs.discord.com/developers/resources/soundboard
- Soundboard Guide: https://support.discord.com/hc/en-us/articles/12612888127767

### 2.11 온보딩, AutoMod, 보안

Community Onboarding은 새 멤버가 질문에 답해 역할과 채널을 선택하게 한다. AutoMod는 위험 콘텐츠를 게시 전 탐지/차단하는 필터 시스템이다. Activity Alerts와 Security Actions는 이상 활동을 감지하고 빠른 조치가 가능하게 한다.

구현 명세:

- onboarding 질문
- 질문 답변별 role/channel assignment
- default channels
- channel browse/customize
- AutoMod rule:
  - keyword filter
  - spam filter
  - mention spam
  - regex rule
  - action: block, timeout, alert, log
- moderation audit log
- activity alert:
  - join raid
  - mention spike
  - message spam spike
- security action:
  - pause invites
  - require verification
  - slowmode escalation

주요 출처:

- Community Onboarding FAQ: https://support.discord.com/hc/en-us/articles/11074987197975-Community-Onboarding-FAQ
- AutoMod FAQ: https://support.discord.com/hc/articles/4421269296535
- Activity Alerts + Security Actions: https://support.discord.com/hc/en-us/articles/17439993574167-Activity-Alerts-Security-Actions

### 2.12 프로필, Nitro, Shop, Quests

Discord는 Nitro/Nitro Basic 구독, Server Boost, Avatar Decorations, Profile Effects, Shop, Server Shop, Quests를 통해 수익화와 개인화를 제공한다. 클론에서는 결제/광고를 바로 구현하지 말고 feature flag와 entitlement 모델을 먼저 설계한다.

구현 명세:

- user profile
- server profile override
- avatar/banner
- status/presence/custom status
- profile decorations/effects/nameplates: entitlement 기반
- subscription plan: FREE, PREMIUM
- server boost: 후순위
- shop catalog: 후순위
- server shop:
  - digital product
  - premium role
- quests:
  - campaign
  - opt-in
  - progress
  - reward claim

주요 출처:

- Nitro: https://support.discord.com/hc/en-us/articles/115000435108-Discord-Nitro
- Avatar Decorations: https://support.discord.com/hc/en-us/articles/13410113109911-Avatar-Decorations
- Profile Effects: https://support.discord.com/hc/en-us/articles/17828465914263-Profile-Effects
- Shop FAQ: https://support.discord.com/hc/en-us/articles/17162747936663-Shop-FAQ
- Server Shop for Members: https://support.discord.com/hc/en-us/articles/17633135983383-Server-Shop-for-Members
- Discord Quests FAQ: https://support.discord.com/hc/en-us/articles/22225719947543-Discord-Quests-FAQ

## 3. 기능 우선순위

### P0: 제품 골격

- 인증/계정
- 서버/채널/역할/권한
- 초대
- 텍스트 메시지
- WebSocket Gateway
- Nuxt Discord shell UI
- Backend TDD harness
- Frontend component/e2e harness

### P1: Discord 핵심 사용성

- 친구/DM/그룹 DM
- presence/typing/read state
- 첨부 파일
- 반응/이모지
- 스레드
- 권한 UI
- 감사 로그

### P2: 커뮤니티 운영

- 포럼
- 온보딩
- AutoMod
- 서버 템플릿
- 이벤트/스테이지

### P3: 미디어

- 음성 채널
- 그룹 DM voice/video
- screen share
- soundboard

### P4: 수익화/고급

- 스티커
- shop/catalog
- premium role
- profile effects/decorations
- quests

## 4. 구현상 핵심 난제

1. 권한 계산은 모든 API, Gateway, UI 가시성에 영향을 준다. 초기부터 별도 Permission Engine으로 분리해야 한다.
2. 채팅 저장소는 append-heavy이며 검색/페이지네이션/삭제 정책이 필요하다.
3. Gateway는 상태 캐시와 이벤트 유실/재연결을 고려해야 한다.
4. voice/video는 Spring Boot만으로 해결할 문제가 아니며 WebRTC SFU가 별도 필요하다.
5. Discord급 기능을 한 모놀리스에서 모두 처리하면 장기적으로 배포/스케일 병목이 생긴다. 단, 초기는 modular monolith로 시작하고 bounded context별 분리를 준비하는 방식이 현실적이다.

## 5. 클론 범위 결정

초기 제품은 "Discord-like Collaboration Platform"으로 정의한다.

포함:

- 서버/채널/권한/초대
- 실시간 채팅
- DM/친구
- 이모지/반응
- 스레드/포럼
- 보이스 기반 구조
- Nuxt 기반 Discord UI shell
- 테스트/QA 자동화

후순위:

- Nitro 완전 결제
- Shop 완전 결제
- Quests 광고 상품
- 로컬 Clips
- 대규모 공개 Discovery 랭킹

제외하지 않고 feature flag로 둔다:

- premium entitlement
- server product
- profile decoration/effects
- soundboard
- stage/event

