# wallet-service

Vivid Pay wallet service: wallets, double-entry ledger, transfers. Java 21, Spring Boot 3.5, PostgreSQL, Flyway, Maven.
Work is tracked in Jira project **VPAY** (https://sithumkaushitha.atlassian.net). Code is in the GitHub org `vivid-pay`.

## Build and test
```bash
mvn verify                          # full build + all tests (needs Docker running: Testcontainers)
mvn -pl wallet-core test -Dtest=ModularityTests   # fast check of module boundaries
mvn -pl wallet-core spring-boot:run # run locally on :8080
```
Local database: start Postgres from the sibling repo (`../base-components`, `docker compose up -d postgres`), then create the database once. Default connection is in `wallet-core/src/main/resources/application.yml`; local credentials are not secrets.

## Layout
- `wallet-core/` is the only Maven module and the deployable. Its packages under `com.sithum.wallet` are Spring Modulith modules: `accounts`, `ledger`, `transfers`, `events`.
- Modules talk through their public API only. No module reads another module's tables. `ModularityTests` fails the build on a violation.
- All tables live in the `wallet` schema (`wallet.db.schema`). Flyway owns the schema; Hibernate only validates.
- `docs/requirements.md` holds requirements; `docs/adr/` holds decisions (copy `0000-template.md`).

## Branching
- `develop` is the integration branch and default. **All work merges to `develop` through a PR.** Never push to `develop`, `main` or `release/*`.
- `main` is what is in production.
- Branch from `develop`: `feature/VPAY-<n>-<short-name>` or `fix/VPAY-<n>-<short-name>` (lowercase, hyphens).
- Release `release/<major>.<release>.<hotfix>` (e.g. `release/1.1.0`): branch from `main`, then merge `develop` into it if everything is ready, otherwise `git cherry-pick -x` the ready commits. Deploy it, merge it into `main` and tag `v<version>`, then merge `main` back into `develop`.
- Hotfix: `release/x.y.(z+1)` from `main` containing only the fix, then the same merge-back.
- Release merges and the `main` -> `develop` back-merge use merge commits. Feature and fix PRs are **squash-merged** (one ticket = one commit).
- Do not cherry-pick a ticket whose Flyway migration depends on an unreleased earlier migration; merge `develop` whole instead.

## Commits and PRs
- Commit and PR title: `VPAY-<n>: imperative summary`.
- One ticket per PR. Link the Jira ticket in the PR description and fill the checklist in the PR template.
- Never merge your own automated PR; a human reviews and merges.

## Definition of Done
- [ ] Every acceptance criterion in the Jira ticket is met
- [ ] Tests added or updated, including edge cases from the criteria
- [ ] `mvn verify` is green
- [ ] Schema change has a new Flyway migration (see rules below)
- [ ] Docs updated; an ADR written if a design decision was made
- [ ] No secrets, no commented-out code, no unrelated changes

## Hard rules
- Never edit a Flyway migration that has been merged. Add a new `V<n>__<description>.sql`.
- No secrets, tokens or real credentials in code, config, tests or commit history.
- Money is `long` minor units plus a currency code. Never `float`, `double` or `BigDecimal` in the domain.
- The database enforces invariants too (constraints, `CHECK (balance >= 0)`), not just the Java code.
- Ledger entries are immutable. Corrections are reversal entries, never updates or deletes.
- Money-moving endpoints are idempotent (idempotency key).

## Working a ticket
1. Read the Jira ticket and its acceptance criteria; move it to **In Progress**.
2. Branch from an up-to-date `develop` using the naming above.
3. Implement with tests; run `mvn verify` and fix failures before pushing. If it cannot be made green, stop and report why.
4. Push, open a PR into `develop`, move the ticket to **In Review**. Stop there.
5. After a human merges, the ticket moves to **Done**.
