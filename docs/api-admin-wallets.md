# Admin API: freeze and unfreeze a wallet

> **Unprotected.** Until the Security epic (VPAY-7) adds admin-only authorisation, anyone who sends a well-formed
> `X-User-Id` header can call `/api/v1/admin/**`, and the actor recorded in the history is whatever id the caller
> claims (ADR-0006). Do not expose it beyond local and test use. VPAY-7 must protect this endpoint.

`PUT /api/v1/admin/wallets/{id}/status`

```json
{ "status": "FROZEN", "reason": "suspected fraud" }
```

| Field | Rules |
|---|---|
| `status` | `ACTIVE` or `FROZEN`; anything else is 400 |
| `reason` | required, not blank; missing or blank is 400 |

Returns `200` with the wallet (same shape as `GET /api/v1/wallets/{id}`).

| Case | Result |
|---|---|
| Status differs from the current one | status changed, one history row written |
| Status equals the current one | `200`, nothing changed, no history row (idempotent) |
| Wallet does not exist | `404` |
| Missing or invalid `X-User-Id` | `401`, nothing changed |

Errors are RFC 9457 problem details (ADR-0004). Unlike the owner endpoints, this one works on any owner's wallet.

## History
Every change appends a row to `wallet_status_history` (`wallet_id`, `from_status`, `to_status`, `actor_id`, `reason`,
`changed_at`). The table is append-only: a trigger rejects updates, deletes and truncates for every database role, the
same way as `ledger_entry` (ADR-0002). The current status stays on `wallet_profile`.

## For other modules
`com.sithum.wallet.accounts.Wallets#isActive(walletId)` is `false` for a frozen wallet and throws
`WalletNotFoundException` for an unknown id. Transfers will use it to refuse moving money.
