\set room_id random(1,1000)
\set cursor random(0,1000000)
-- :table is supplied only by run.ps1's baseline/date_range/date_hash allowlist.
SELECT chat_room_id, sequence, created_at
FROM :table
WHERE chat_room_id = 'room-' || :room_id
  AND sequence > :cursor
ORDER BY sequence
LIMIT 50;
