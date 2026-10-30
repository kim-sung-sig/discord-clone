# Gateway

다른 context에서 일어난 일을 연결된 User에게 순서대로, 빠짐없이 전달하는 context다. ADR은 이를 durable 상태를 가진 Gateway Control과 소켓을 다루는 WebSocket Transport로 나눈다. 이 용어집은 둘이 함께 쓰는 말을 정의한다.

## Language

**GatewayEvent**:
User 한 명에게 전달할 이벤트 하나다. 종류(**GatewayEventType**), 대상 Guild·채널, payload, **UserEventSequence**를 가진다.
_Avoid_: dispatch(단독), message(채팅 메시지와 헷갈림)

**GatewayEventType**:
GatewayEvent의 종류다. 예: `MESSAGE_CREATE`, `MESSAGE_UPDATE`, `MESSAGE_DELETE`, `VOICE_STATE_UPDATE`. enum으로 다룬다.
_Avoid_: 문자열 상수로 종류를 비교하는 것. 감사 기록의 `MESSAGE_DELETED`(AuditLogAction)와는 다른 어휘다.

**UserEventSequence**:
User 한 명에게 가는 GatewayEvent마다 매기는 증가 번호다. 재개(resume)와 ACK의 기준이다.
_Avoid_: sequence(단독), 메시지의 **ChannelSequence**

**GatewaySession**:
화면 하나가 Gateway에 연결된 동안의 세션이다. identify로 시작하고, 끊겼다가 resume으로 이어질 수 있다.
_Avoid_: Session(단독), socket(연결 자체를 뜻할 때만 쓴다)

**GatewaySessionCursor**:
GatewaySession이 어디까지 전달받고 ACK했는지 나타내는 위치다. **DeliveryEpoch**와 소유 노드를 함께 가진다.
_Avoid_: cursor(단독)

**DeliveryEpoch**:
세션의 전달 권한이 다른 노드로 넘어갈 때마다 올라가는 번호다. 오래된 노드가 이미 넘어간 세션에 전달하지 못하게 막는다(펜싱).
_Avoid_: 소유 노드 id나 epoch를 고정값으로 두는 것

**Opcode**:
화면과 Gateway가 주고받는 frame의 종류다: identify, heartbeat, resume, ACK, dispatch.
_Avoid_: 문자열 switch로 frame을 판별하는 것

## Relationships

- **GatewaySession** 하나는 **GatewaySessionCursor** 하나를 가진다.
- **GatewayEvent**는 User마다 **UserEventSequence** 순서로 쌓인다.
- Message의 **MessagePublished**는 `MESSAGE_CREATE` **GatewayEvent**가 된다.

## Example dialogue

> **Dev:** "재연결하면 메시지의 sequence부터 다시 보내면 되죠?"
> **Domain expert:** "Gateway는 **UserEventSequence**로 이어 받습니다. 채널마다 다른 **ChannelSequence**로는 한 사람에게 간 이벤트 전체 순서를 알 수 없어요."

## Flagged ambiguities

- `InMemoryGatewayService`는 운영에서도 쓰는 Gateway 구현인데, 이름이 시험용처럼 보인다. 소유 노드 id가 `"in-memory"`, `deliveryEpoch`가 `1L`로 고정돼 있어 여러 노드 펜싱이 동작하지 않을 수 있다(U2에서 확인하고 고친다).
- 웹 화면은 아직 GatewaySession을 열지 않는다.
  - 소켓 수명 함수(`createGatewaySocketLifecycle`)는 시험에서만 쓰인다.
  - 반면 `stores/shell.ts`는 dispatch 정규화 함수(`normalizeGatewayDispatch`, `toShellGatewayEvent`)를 운영 코드에서 쓴다.
  - U2에서 소켓을 연결한다.
