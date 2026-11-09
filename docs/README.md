# wallet-service docs

Docs about this service. Requirements and system-wide decisions live in the
[platform](https://github.com/vivid-pay/platform) repo; start with its
[ADR index](https://github.com/vivid-pay/platform/blob/develop/docs/adr/README.md), which lists the ADRs of
every repo and the global numbering rule.

## In this repo
| Doc | What it is |
|---|---|
| [adr/0002](adr/0002-ledger-and-balance-model.md) | Double-entry ledger with a stored balance projection (and table ownership follow-ups) |
| [adr/0005](adr/0005-sonarqube-quality-gate.md) | SonarQube Cloud quality gate on new code |
| [adr/0006](adr/0006-temporary-owner-identity-header.md) | Temporary caller identity from the `X-User-Id` header |
| [api-money-conventions.md](api-money-conventions.md) | How the API represents money: minor units and the exponent per currency |
| [api-admin-wallets.md](api-admin-wallets.md) | Freeze/unfreeze endpoint (unprotected until VPAY-7), status history and the `Wallets.isActive` rule |
| [api-pagination.md](api-pagination.md) | `page`/`size` parameters, the maximum page size and the page response shape |

## In the platform repo
| Doc | What it is |
|---|---|
| [requirements.md](https://github.com/vivid-pay/platform/blob/develop/docs/requirements.md) | Functional and non-functional requirements |
| [ADR-0001](https://github.com/vivid-pay/platform/blob/develop/docs/adr/0001-microservices-with-three-services.md) | Microservices, limited to three deliberate services |
| [ADR-0003](https://github.com/vivid-pay/platform/blob/develop/docs/adr/0003-money-representation.md) | Money as integer minor units plus a currency code |
| [ADR-0004](https://github.com/vivid-pay/platform/blob/develop/docs/adr/0004-api-error-format.md) | API errors as RFC 9457 problem details |
| [ADR template](https://github.com/vivid-pay/platform/blob/develop/docs/adr/0000-template.md) | Copy this for a new ADR |
