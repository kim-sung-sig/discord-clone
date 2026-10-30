# Media

첨부 파일과 음성·영상 연결을 소유하는 context다. 파일 내용과 미디어 전송은 외부(객체 저장소, LiveKit)가 맡고, 이 context는 그 메타데이터와 접근 권한을 관리한다.

## Language

**Attachment**:
메시지에 붙은 업로드 파일의 메타데이터다. 상태(업로드 중, 검사 중, 사용 가능, 거부)와 검사 결과를 가진다. 파일 내용은 메시지 트랜잭션에 저장하지 않는다.
_Avoid_: file(단독), upload

**VoiceState**:
User 한 명이 음성 채널 하나에서 가진 상태다: 음소거, 스피커 끔, 말하는 중, 화면 공유.
_Avoid_: call state, voice room state

**VoiceJoinToken**:
User가 음성 채널에 들어가기 위해 LiveKit에서 쓰는 짧은 수명의 토큰이다. 채널 연결 권한(`CONNECT`)이 있을 때만 발급한다.
_Avoid_: VoiceRoomToken의 `room`을 채널과 다른 개념처럼 쓰는 것

**StageSession**:
음성 채널 안에서 발표자와 청중을 나누어 진행하는 무대 세션이다.
_Avoid_: Session(단독)

## Relationships

- **Attachment**는 Message 하나에 연결된다.
- **VoiceState**는 (User, 음성 Channel)마다 하나다.
- **StageSession**은 음성 Channel 하나에 속한다.

## Example dialogue

> **Dev:** "음성 채널이 보이면 토큰을 주면 되죠?"
> **Domain expert:** "보이는 것(`VIEW_CHANNEL`)과 들어가는 것(`CONNECT`)은 다른 권한입니다. **VoiceJoinToken**은 `CONNECT`가 있을 때만 줍니다."

## Flagged ambiguities

- 음성 채널, voice room, call이 섞여 쓰인다. Guild 음성 채널은 **Channel**(`GUILD_VOICE`)이고, LiveKit의 room은 그 채널에 붙는 연동 이름일 뿐이다. 1:1 통화(`GroupCallState`)는 GroupDm 위의 통화로 따로 정리한다.
- `Permission.CONNECT`는 정의만 있고 토큰 발급 때 검사하지 않는다(`VoiceController`는 보기 권한만 확인한다). U5에서 고친다.
- `StageSession`은 지금 experience 모듈에 있다. 용어상 Media이므로 분리할 때 옮긴다.
