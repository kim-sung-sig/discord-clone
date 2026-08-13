\set room_id random(1,1000)
\set cursor random(0,1000000)
SELECT chat_room_id, sequence, created_at
FROM messages_baseline
WHERE chat_room_id = 'room-' || :room_id
  AND sequence > :cursor
ORDER BY sequence
LIMIT 50;
