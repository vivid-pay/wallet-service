# ADR-0002: Double-entry ledger with a stored balance projection

- Status: accepted
- Date: 2026-11-01

## Context
The service holds customers' money, so every balance must be explainable, and no
money may be created or lost by a bug, a crash or a concurrent request. Two
questions have to be settled before the first migration:

1. Where does a balance "live": derived from history, stored directly, or both?
2. How do transactions that involve money from outside the system (top-ups,
   withdrawals, fees) fit the same bookkeeping as wallet-to-wallet transfers?

## Options considered

### Balance
1. **Computed only.** Balance = `SUM(entries)` on every read. It cannot drift, but
   reads slow down as history grows and the sum must be recomputed inside the
   transfer's critical section, which holds row locks longer.
2. **Stored only.** A `balance` column that is updated in place. Fast, but there is no
   history to prove it right, and a bug or manual edit silently corrupts it.
3. **Entries are the truth, plus a stored balance as a cached projection.** Entries
   are immutable. The balance column is updated in the same database transaction
   as the entries that change it.

### Counterparty of external money
1. **Wallets only.** A top-up is a one-sided credit. The books never balance and
   reconciliation against the outside world is weak.
2. **System accounts.** Wallets are one kind of ledger account. System accounts
   (external funding, fee revenue) are the counterparty, so every transaction
   sums to zero.

## Decision
- **Double-entry ledger.** Every money movement is a transaction made of two or more
  immutable ledger entries whose signed amounts sum to zero. Corrections are new
  reversing entries, never updates or deletes.
- **Entries are the source of truth; the stored balance is a cached projection.** The
  balance on an account is updated in the same database transaction as its
  entries, so they cannot diverge through a crash.
- **System accounts.** A ledger account has a type (`WALLET` or `SYSTEM`). System accounts
  such as `EXTERNAL_FUNDING` and `FEES` (one per currency) are the counterparty for
  top-ups, withdrawals and fees.
- **Sign convention.** An entry's amount is positive when it increases that account's
  balance and negative when it decreases it.
- **Reconciliation.** A scheduled job verifies, per account, that `balance = SUM(entries)`
  and, across the ledger, that each transaction's entries sum to zero. A mismatch is
  an alarm; the entries are trusted over the cached balance.
- **Database-enforced invariants.** Wallet accounts have `CHECK (balance >= 0)`. System
  accounts are exempt: `EXTERNAL_FUNDING` goes negative as money is taken in, and that
  figure is what is reconciled against the real bank balance.

Example (Alice tops up 100, then sends 30 to Bob):

| Transaction | Account | Amount |
|---|---|---|
| Top-up | EXTERNAL_FUNDING | -100 |
| Top-up | Alice | +100 |
| Transfer | Alice | -30 |
| Transfer | Bob | +30 |

## Consequences
- Reads of a balance are a single-row lookup; the history stays complete for audit.
- Every write path that touches a balance must also write its entries in one
  transaction; this belongs in one place in the `ledger` module, not scattered.
- The reconciliation job becomes a required piece of the system, not an optional extra.
- Transfers lock the affected account rows in a consistent order (by id) to avoid
  deadlocks and to make the balance check-and-update atomic.
- Cross-currency movement is out of scope: an entry's account and amount share one
  currency, and a transaction stays within one currency. FX is a later ADR.
- Entry immutability must be enforced in the database as well (no `UPDATE`/`DELETE` for the
  application role), not only by convention.

## Open follow-ups (decided in VPAY-17, the data-model story)
- Which module owns which table. A likely split: `ledger` owns ledger accounts and
  entries (including the balance), and `accounts` owns the wallet profile (owner,
  status) and refers to its ledger account by id, with no cross-module foreign key.
- Whether the "sums to zero" rule is also enforced by a deferred database constraint
  or only by the service plus reconciliation.
