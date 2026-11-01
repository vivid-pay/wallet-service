# ADR-0001: Microservices, limited to three deliberate services

- Status: accepted
- Date: 2026-11-01

## Context
This is a personal project with no team, built by one developer with AI assistance, for
fun and for learning production standards: DevOps, security, observability and
deployment to AWS. The backend comes first; what follows after is undecided and the
final product shape is not yet known.

The system handles money, so correctness of the core ledger matters more than anything
else. The learning goals include service-to-service communication, because most
companies run microservices.

## Options considered
1. **Modular monolith first, extract services later.** The usual default for a product:
   simplest to build, run and trace, with module boundaries enforced by Spring Modulith.
   Weakness for this project: it teaches almost nothing about service-to-service
   communication, independent deployment or distributed failure, which are explicit
   learning goals.
2. **Microservices from the start, uncapped.** Maximum realism, but one person cannot
   operate many services, and services created without a real need add cost without
   teaching anything.
3. **Microservices, deliberately limited to three.** Realistic enough to practise
   synchronous and asynchronous communication, deployment and observability, while
   staying operable by one person.

## Decision
Build **microservices, limited to at most three services written by us**:

| Service | Responsibility | What it teaches |
|---|---|---|
| `wallet-service` | Accounts, ledger and transfers: the core. Also the scheduled reconciliation job. | Owns the data; publishes events |
| `funding-service` | Simulated card/bank provider: top-ups and withdrawals | Synchronous calls from wallet-service, asynchronous callbacks, sagas and compensation |
| `notification-service` | Emails/push after transfers | Asynchronous event consumption |

- Identity (Keycloak) and an API gateway are configured, not written, and do not count
  against the limit of three.
- **Ledger and transfers are never split.** A transfer must debit one account and credit
  another in a single database transaction; splitting them would turn the most critical
  path into a distributed transaction that needs compensation, in-between states and a
  recovery process. Reconciliation also stays inside `wallet-service` because it needs
  the ledger's data; its own service would force us to expose or copy the ledger.
- Inside `wallet-service`, the `accounts`, `ledger`, `transfers` and `events` packages
  remain enforced modules (Spring Modulith, `ModularityTests`). Module boundaries inside
  a service and service boundaries between services follow the same rule: no reaching
  into another's data, only its public interface.
- **Build order:** `wallet-service` first. `notification-service` is added when there is
  a real event to consume, and `funding-service` when top-up is implemented. Services are
  not created empty up front.
- **Naming and layout:** each service is its own repository named `{name}-service` in the
  `vivid-pay` organization, with its own pipeline and database.

### Communication rules
- **Synchronous (request/response)** when the caller needs the answer to continue and the
  user is waiting, for example `wallet-service` asking `funding-service` to start a
  top-up. Every such call has a timeout, bounded retries with an idempotency key, and a
  circuit breaker.
- **Asynchronous (events)** for facts that have already happened and that others react to
  independently, for example `TransferCompleted` consumed by `notification-service`, and
  the funding result coming back from `funding-service` as an event.
- **A transfer never fails because of a notification.** If `notification-service` is down,
  the transfer completes, the event is kept durably (outbox pattern plus a broker), and it
  is delivered when the consumer returns. Delivery is at-least-once, so consumers must be
  idempotent.
- **The core invariant stays in one database transaction.** Compensation (sagas) is used
  only where a transaction cannot span systems, which is the funding flow.
- **Each service owns its database.** Services exchange data only through APIs and events,
  both versioned. Every request carries a correlation ID, propagated across services.
- Service-to-service authentication is decided in the Security epic; the choice of broker
  is its own ADR.

## Consequences
**Harder (accepted):**
- More to maintain and deploy: separate repositories, pipelines, images and infrastructure
  per service, and a bigger local development setup.
- Failures between services (timeouts, partial failure, duplicate delivery) must be handled
  explicitly, and some states are only eventually consistent.
- Tracing a request across services needs correlation IDs and distributed tracing
  (OpenTelemetry), moving observability from optional to essential.
- Testing needs contract tests and more integration tests.

**Easier or better:**
- Services can be scaled and deployed independently.
- The technology of each service can be chosen separately. In practice, with one
  developer, all three will probably stay on Java.
- Separate teams could own separate services in future; this is a future benefit and not a
  current one.
- Learning value: service-to-service communication, sagas, event-driven design, per-service
  delivery pipelines and deployment to AWS.

**Revisit this decision if:** the operational overhead of three services starts to block
learning and progress, in which case merging a service back into `wallet-service` is a
legitimate option, or if a fourth service is proposed (it needs its own ADR).
