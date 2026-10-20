ALTER TABLE dm_messages DROP CONSTRAINT dm_messages_sender_id_fkey;
ALTER TABLE dm_messages ADD CONSTRAINT dm_messages_sender_id_fkey
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;
