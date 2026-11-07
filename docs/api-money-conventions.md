# API money conventions

Follows [ADR-0003](adr/0003-money-representation.md). Every client of the wallet API must read this.

- **Amounts are integers in minor units.** `balance: 1234` with `currency: "USD"` is $12.34. The API
  never returns decimals, strings or floating point for money.
- **The currency always travels with the amount**, as an uppercase ISO 4217 code, for example
  `{ "currency": "USD", "balance": 1234 }`.
- **The exponent comes from the currency**, not from an assumption of two decimals. To display an amount,
  divide by 10^exponent:

| Currency | Exponent | Minor unit | `1234` displays as |
|---|---|---|---|
| JPY | 0 | yen | ¥1,234 |
| USD, EUR, LKR | 2 | cent | 12.34 |
| KWD | 3 | fils | 1.234 |

- Formatting for humans is the client's job. Clients that send amounts must send integers in minor units too.
- **Supported currencies** are a configured list (`wallet.ledger.supported-currencies`, launch value
  `USD,EUR,LKR`). JPY and KWD above illustrate the exponent rule; they are not supported yet.
  Anything else, including lowercase or malformed codes, is rejected with `400`.
