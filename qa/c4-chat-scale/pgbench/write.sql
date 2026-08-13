\set room_id random(1,1000)
\set sequence random(1,1000000000)
\set day_offset random(0,29)
\set retry_key random(1,1000000000)
INSERT INTO messages_baseline (chat_room_id, event_date, sequence, content, idempotency_key)
VALUES ('room-' || :room_id, DATE '2026-01-01' + :day_offset, :sequence, 'benchmark message', 'idem-' || :retry_key)
ON CONFLICT (chat_room_id, sequence) DO NOTHING;
