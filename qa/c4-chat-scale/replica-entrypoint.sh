#!/bin/sh
set -eu

PGDATA="${PGDATA:-/var/lib/postgresql/data/pgdata}"
export PGDATA
export PGPASSWORD="${REPLICATION_PASSWORD:-dev_replication_password}"

if [ ! -s "$PGDATA/PG_VERSION" ] || [ ! -e "$PGDATA/standby.signal" ]; then
  rm -rf "$PGDATA"/*
  mkdir -p "$PGDATA"
  chown postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
  until pg_isready -h primary -p 5432 -U c4_user -d c4chat >/dev/null 2>&1; do
    sleep 1
  done
  if gosu postgres env PGPASSWORD="$PGPASSWORD" psql -h primary -U replication -d c4chat -Atqc "SELECT 1 FROM pg_replication_slots WHERE slot_name = 'c4_chat_scale_slot'" | grep -q 1; then
    gosu postgres pg_basebackup -h primary -D "$PGDATA" -U replication -R -S c4_chat_scale_slot -X stream
  else
    gosu postgres pg_basebackup -h primary -D "$PGDATA" -U replication -R -S c4_chat_scale_slot -X stream -C
  fi
fi

test -e "$PGDATA/standby.signal"
grep -q 'primary_conninfo' "$PGDATA/postgresql.auto.conf"
chown -R postgres:postgres "$PGDATA"
chmod 700 "$PGDATA"
exec gosu postgres "$@"
