# wallet-service

[![CI](https://github.com/vivid-pay/wallet-service/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/vivid-pay/wallet-service/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=vivid-pay_wallet-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=vivid-pay_wallet-service)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=vivid-pay_wallet-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=vivid-pay_wallet-service)

A digital wallet built with production standards as a learning project.
Java 21, Spring Boot 3, PostgreSQL, Maven.

Part of **Vivid Pay**. The vision, roadmap and working agreements are in the [platform](https://github.com/vivid-pay/platform) repo.

## Layout
- `wallet-core/` – the deployable core of `wallet-service`, organised as modules: `accounts`, `ledger`, `transfers`, `events`
- `docs/requirements.md` – functional and non-functional requirements
- `docs/adr/` – architecture decision records

## Run locally
```bash
docker compose -f ../base-components/docker-compose.yml up -d postgres
mvn -pl wallet-core spring-boot:run
```

## Test
```bash
mvn verify   # needs Docker running (Testcontainers)
```
