# Wallet – Requirements

Status: draft v0.1. Edit freely; changes to rules should get an ADR.

## Actors
- **Customer** – holds a wallet, tops up, transfers, withdraws.
- **Admin / operator** – supports customers, freezes accounts, investigates disputes.
- **System** – scheduled jobs (reconciliation, statements).

## Functional requirements

### MVP
| ID | Requirement |
|----|-------------|
| FR-1 | Register and log in (delegated to Keycloak, Phase 2) |
| FR-2 | Create a wallet per user; one currency per wallet |
| FR-3 | Top up via a simulated payment provider |
| FR-4 | Transfer between two wallets: atomic, idempotent, concurrency-safe |
| FR-5 | View balance and paginated, filterable transaction history |
| FR-6 | Withdraw to a simulated bank account |
| FR-7 | Admin can freeze / unfreeze a wallet |

### Business rules
- BR-1 Balance never goes negative.
- BR-2 Per-transaction and daily limits apply.
- BR-3 Every money movement is a double-entry ledger record; entries are immutable. Corrections are reversal entries.
- BR-4 Repeating a request with the same idempotency key never moves money twice.
- BR-5 Transaction states: `PENDING -> COMPLETED | FAILED | REVERSED`.

### Later
Notifications, statements (PDF/CSV), FX conversion, payment requests, scheduled payments, daily reconciliation, admin audit trail.

## Non-functional requirements
- **Correctness** – no lost or double-spent money under concurrency or crashes.
- **Security** – authN/authZ on every endpoint; TLS in transit, encryption at rest.
- **Availability** – SLO for transfers to be defined (initial target 99.9%).
- **Auditability** – any balance can be explained from the ledger.
- **Observability** – requests traceable by ID across services.

## Decisions made
- Balance model and top-up/fee bookkeeping: entries are the truth, stored balance is a
  cached projection, system accounts are the counterparty ([ADR-0002](adr/0002-ledger-and-balance-model.md)).
- Money representation: integer minor units plus an ISO 4217 code ([ADR-0003](adr/0003-money-representation.md)).

## Open questions
- Which currencies are supported at launch? (a configured list; decided in the "create a wallet" story)
- Which module owns which table (`ledger` vs `accounts`)? (decided in the wallet data-model story)
