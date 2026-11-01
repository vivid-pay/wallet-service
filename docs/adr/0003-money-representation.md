# ADR-0003: Money as integer minor units plus a currency code

- Status: accepted
- Date: 2026-11-01

## Context
Amounts of money flow through the API, the domain code and the database. The
representation must be exact (no rounding drift across millions of
transactions), hard to misuse (no mixing currencies or scales by accident) and
efficient.

## Options considered
1. **`double` / `float`.** Cannot represent most decimals exactly
   (`0.1 + 0.2 = 0.30000000000000004`). Rejected outright.
2. **`BigDecimal` in Java and `NUMERIC(19,4)` in PostgreSQL.** Exact, but every
   calculation needs an explicit scale and rounding mode, a value like `12.3456`
   can exist although no currency has that precision, and mixing scales is an easy bug.
3. **Integer minor units (`long` / `BIGINT`) plus an ISO 4217 currency code.** Exact,
   fast and compact. The smallest unit of the currency (cents, paise) is the
   unit of account.

## Decision
- Amounts are stored and processed as **whole minor units**: `BIGINT` in PostgreSQL
  and `long` in Java. `1234` with currency `USD` means $12.34.
- The **currency is always stored with the amount** (`CHAR(3)`, ISO 4217, uppercase).
  The number of minor-unit digits comes from the currency (JPY 0, USD 2, KWD 3), never
  from a global assumption of two.
- In the domain, money is a small value type (amount in minor units plus currency).
  Arithmetic uses `Math.addExact` / `subtractExact`, so overflow fails loudly, and
  operations on two different currencies are rejected.
- Only a **configured list of supported currencies** is accepted, not every code.
- The **API exchanges integers in minor units** with the currency alongside, for
  example `{ "amount": 1234, "currency": "USD" }`. Formatting for humans (`12.34`) is a
  client concern.
- No floating point and no `BigDecimal` in the domain model. Where a rate must be
  applied (fees, FX later), the rounding mode is specified in code and tested.

## Consequences
- Exact arithmetic and simple, indexable columns; sums in SQL are plain integer sums.
- Clients must know the currency's minor-unit exponent to display amounts, which the
  API must document.
- Percentage-based fees and FX will need an explicit rounding policy (decided in the ticket
  that introduces them, and recorded as its own ADR).
- A `long` holds roughly 9.2 x 10^18 minor units, which is far beyond any realistic balance,
  and overflow is still guarded by the exact-arithmetic methods.
