SELECT 'CREATE DATABASE discord'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'discord'
)\gexec
