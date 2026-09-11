---
slug: T147-docker-compose-resource-topology-alignment
ticket: T147
phase: design
hub: "[[T147-docker-compose-resource-topology-alignment]]"
---
> 🧭 [[T147-docker-compose-resource-topology-alignment]] · PDCA: [[T147-docker-compose-resource-topology-alignment.plan]] → **design** → [[T147-docker-compose-resource-topology-alignment.analysis]] → [[T147-docker-compose-resource-topology-alignment.report]] → [[T147-docker-compose-resource-topology-alignment.feedback]]

# T147 Docker Compose Resource Topology Alignment Design

Date: 2026-05-20
Slice: T147 Docker Compose Resource Topology Alignment

## Compose Resource Contract

The repository Compose file should expose the same central local endpoints used by Spring and Nuxt examples:

| Service | Host Port | Container Port |
| --- | ---: | ---: |
| `postgres-source` | `15432` | `5432` |
| `ms-redis` | `16379` | `6379` |
| `ms-kafka` | `29092` | `29092` |

## Redis Password

Redis runs with `--requirepass`, defaulting to `dev_password`, matching `.env.example` and `application-redis.yml`.

## Kafka Broker

The existing Redpanda image remains the Kafka-compatible local broker. Its advertised Kafka address is moved to `127.0.0.1:29092`.
