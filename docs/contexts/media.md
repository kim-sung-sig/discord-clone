# Media

첨부 파일과 음성 연결을 소유하는 context다(ADR의 Media/Voice). 파일 내용과 미디어 전송은 외부(객체 저장소, LiveKit)가 맡고, 이 context는 그 메타데이터와 접근 권한을 관리한다.

## Language

**Attachment**:
메시지에 붙는 업로드 파일의 메타데이터다. 업로드 진행 상태(**AttachmentStatus**)와 검사 결과(**AttachmentScanStatus**)를 따로 가진다. 파일 내용은 메시지 트랜잭션에 저장하지 않는다.
_Avoid_: file(단독), upload, 두 상태를 하나로 합쳐 부르는 것

**AttachmentStatus**:
업로드 진행 상태다: `PENDING`(업로드 전), `UPLOADED`(업로드됨), `ATTACHED`(메시지에 연결됨).

**AttachmentScanStatus**:
파일 검사 결과다: `CLEAN`, `BLOCKED`, `UNAVAILABLE`(검사할 수 없음).

**VoiceState**:
User 한 명이 음성 채널 하나에서 가진 상태다: 음소거, 스피커 끔, 말하는 중, 화면 공유.
_Avoid_: call state, voice room state

**VoiceJoinToken**:
User가 음성 채널에 들어가기 위해 LiveKit에서 쓰는 짧은 수명의 토큰이다. 채널 연결 권한(`CONNECT`)이 있을 때만 발급한다.
_Avoid_: VoiceRoomToken(지금 코드 이름이다. U5에서 바꾼다), room을 채널과 다른 개념처럼 쓰는 것

## Relationships

- **Attachment**는 Message 하나에 연결된다.
- **VoiceState**는 (User, 음성 Channel)마다 하나다.
- Guild 음성 채널은 Community의 **Channel**(`GUILD_VOICE`)이다. LiveKit의 room은 그 채널에 붙는 연동 이름일 뿐이다.

## Example dialogue

> **Dev:** "음성 채널이 보이면 토큰을 주면 되죠?"
> **Domain expert:** "보이는 것(`VIEW_CHANNEL`)과 들어가는 것(`CONNECT`)은 다른 권한입니다. **VoiceJoinToken**은 `CONNECT`가 있을 때만 줍니다."

## Flagged ambiguities

- 음성 채널, voice room, call이 섞여 쓰인다. Guild 음성 채널은 **Channel**(`GUILD_VOICE`)이다. 그룹 대화의 통화(`GroupCallState`)는 Community의 **GroupDm**에 붙은 상태이고, Media의 VoiceState와 다르다.
- `Permission.CONNECT`는 정의만 있고 토큰 발급 때 검사하지 않는다(`VoiceController`는 보기 권한만 확인한다). U5에서 고친다.
- Stage(발표자·청중 무대)는 ADR(T171-C0 §3.2)에 따라 experience 모듈과 함께 Community에 둔다(**StageSession**, `community.md`).
