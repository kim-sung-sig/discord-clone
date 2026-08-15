-- C4 benchmark schema only; production messages is never modified.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'replication') THEN
        CREATE ROLE replication WITH REPLICATION LOGIN PASSWORD 'dev_replication_password';
    END IF;
END $$;

-- idempotency_key is unique per chat room and event date; duplicate keys are rejected by the constraint.
CREATE TABLE messages_template (
    chat_room_id text NOT NULL,
    event_date date NOT NULL,
    sequence bigint NOT NULL,
    content text NOT NULL,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_template_unique UNIQUE (chat_room_id, sequence, event_date),
    CONSTRAINT messages_template_idempotency_unique UNIQUE (idempotency_key, event_date, chat_room_id)
);

CREATE TABLE messages_baseline (LIKE messages_template INCLUDING ALL);

CREATE TABLE messages_date_range (
    chat_room_id text NOT NULL,
    event_date date NOT NULL,
    sequence bigint NOT NULL,
    content text NOT NULL,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_date_range_unique UNIQUE (chat_room_id, sequence, event_date),
    CONSTRAINT messages_date_range_idempotency_unique UNIQUE (idempotency_key, event_date, chat_room_id)
) PARTITION BY RANGE (event_date);

CREATE TABLE messages_date_hash (
    chat_room_id text NOT NULL,
    event_date date NOT NULL,
    sequence bigint NOT NULL,
    content text NOT NULL,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT messages_date_hash_unique UNIQUE (chat_room_id, sequence, event_date),
    CONSTRAINT messages_date_hash_idempotency_unique UNIQUE (idempotency_key, event_date, chat_room_id)
) PARTITION BY RANGE (event_date);

DO $$
DECLARE
    day date := DATE '2026-01-01';
    range_name text;
    hash_name text;
    bucket integer;
BEGIN
    FOR i IN 0..29 LOOP
        range_name := format('messages_date_range_%s', to_char(day + i, 'YYYYMMDD'));
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF messages_date_range FOR VALUES FROM (%L) TO (%L)',
            range_name, day + i, day + i + 1
        );

        range_name := format('messages_date_hash_%s', to_char(day + i, 'YYYYMMDD'));
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF messages_date_hash FOR VALUES FROM (%L) TO (%L) PARTITION BY HASH (chat_room_id)',
            range_name, day + i, day + i + 1
        );
        FOR bucket IN 0..15 LOOP
            hash_name := format('%s_bucket_%s', range_name, bucket);
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF %I FOR VALUES WITH (MODULUS 16, REMAINDER %s)',
                hash_name, range_name, bucket
            );
        END LOOP;
    END LOOP;
END $$;
