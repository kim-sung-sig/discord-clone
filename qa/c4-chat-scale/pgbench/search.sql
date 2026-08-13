\set room_id random(1,1000)
\set start_offset random(0,28)
\set end_offset random(:start_offset,29)
SELECT count(*)
FROM messages_baseline
WHERE chat_room_id = 'room-' || :room_id
  AND event_date >= DATE '2026-01-01' + :start_offset
  AND event_date < DATE '2026-01-01' + :end_offset + 1;
