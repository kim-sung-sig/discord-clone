#!/bin/sh
set -eu

tmp="$PGDATA/pg_hba.conf.tmp"
{
  cat <<'EOF'
host replication replication 0.0.0.0/0 scram-sha-256
host c4chat replication 0.0.0.0/0 scram-sha-256
EOF
  cat "$PGDATA/pg_hba.conf"
} > "$tmp"
mv "$tmp" "$PGDATA/pg_hba.conf"
