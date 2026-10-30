# Identity

사람의 계정과 인증을 소유하는 context다. 다른 context는 사람을 `UserId`로만 알고, 로그인 상태는 Identity가 발급한 access JWT로 확인한다.

## Language

**User**:
로그인할 수 있는 계정 하나다. `UserId`, 사용자 이름(username), 표시 이름(displayName)을 가진다.
_Avoid_: account(코드에서), member(Guild 소속을 뜻할 때만 GuildMember)

**DisplayName**:
다른 사람에게 보이는 이름이다. 비어 있으면 username으로 대신한다.
_Avoid_: nickname(서버별 별명 기능은 아직 없다), authorName

**AccessToken**:
Identity가 Ed25519 키로 서명한 짧은 수명의 JWT다. 다른 서비스는 공개 키로 직접 검증하고, 요청마다 Identity를 부르지 않는다.
_Avoid_: session token, bearer(헤더 이름으로만)

**RefreshSession**:
access JWT를 다시 발급받기 위한, DB에 저장되는 로그인 세션이다. httpOnly cookie로 전달되며, rotation 중 재사용이 감지되면 세션 묶음 전체를 끊는다.
_Avoid_: Session(단독), login session

**GlobalRole**:
Guild와 상관없는 플랫폼 전체 권한이다(예: `SECURITY_ADMIN`). Guild 안의 **Role**과 다르다.
_Avoid_: admin flag, role(단독)

**StepUpMfa**:
위험한 운영 작업 직전에 요구하는 추가 인증(WebAuthn, TOTP) 확인이다.
_Avoid_: 2FA(화면 문구 말고는)

## Relationships

- **User** 하나는 **RefreshSession**을 0개 이상 가진다.
- **User**는 **GlobalRole**을 0개 이상 가진다.
- 다른 context는 **User**를 `UserId`로만 참조한다.

## Example dialogue

> **Dev:** "메시지 목록에 작성자 이름이 필요한데 users table을 join해도 되나요?"
> **Domain expert:** "아니요. Message는 `UserId`만 가집니다. 이름은 Identity의 조회 port로 받아서 **DisplayName**으로 보여 주세요."

## Flagged ambiguities

- `Session`은 네 가지 뜻으로 쓰였다. Identity에서는 **RefreshSession**만 쓴다. Gateway 연결은 `GatewaySession`, 음성 무대는 `StageSession`이다.
