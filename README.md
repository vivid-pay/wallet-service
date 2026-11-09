# wallet-service

[![CI](https://github.com/vivid-pay/wallet-service/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/vivid-pay/wallet-service/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=vivid-pay_wallet-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=vivid-pay_wallet-service)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=vivid-pay_wallet-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=vivid-pay_wallet-service)

A digital wallet built with production standards as a learning project.
Java 21, Spring Boot 3, PostgreSQL, Maven.

Part of **Vivid Pay**. The vision, roadmap and working agreements are in the [platform](https://github.com/vivid-pay/platform) repo.

## Layout
- `wallet-core/` – the deployable core of `wallet-service`, organised as modules: `accounts`, `ledger`, `transfers`, `events`
- `docs/` – service-level docs and ADRs, indexed in [docs/README.md](docs/README.md); requirements and system-wide ADRs are in the [platform](https://github.com/vivid-pay/platform/tree/develop/docs) repo

## Run locally
```bash
docker compose -f ../base-components/docker-compose.yml up -d postgres
mvn -pl wallet-core spring-boot:run
```

## Container image
Build (no local JDK needed, only Docker):
```bash
docker build -t wallet-service:local .
```
Run against the base-components Postgres (see "Run locally" to start it and create the `wallet-service` database).
All configuration is passed as environment variables; none is baked into the image:
```bash
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/wallet-service \
  -e DB_USER=postgres \
  -e DB_PASSWORD=password \
  wallet-service:local
curl localhost:8080/actuator/health   # {"status":"UP",...}
```
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA` and `SUPPORTED_CURRENCIES` are read from the environment (defaults in
`wallet-core/src/main/resources/application.yml` are for local development only). The container runs as a non-root user
(uid 10001). `host.docker.internal` reaches Postgres on the host from Docker Desktop; on Linux add
`--add-host=host.docker.internal:host-gateway`.

**Local use only:** do not expose the container beyond your machine until identity for deployed environments is decided
([ADR-0006](docs/adr/0006-temporary-owner-identity-header.md): `X-User-Id` is not secure).

## Admin endpoints
`/api/v1/admin/**` (freeze/unfreeze a wallet, see [docs/api-admin-wallets.md](docs/api-admin-wallets.md)) is **not
protected** until the Security epic (VPAY-7). Use it only locally and in tests.

## Test
```bash
mvn verify   # needs Docker running (Testcontainers)
```
