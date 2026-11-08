# API pagination

List endpoints take `page` and `size` query parameters and return a page object.

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | Zero-based. Must be an integer `>= 0`. |
| `size` | `20` | Integer from `1` to `100` (the maximum page size). |

A `size` above the maximum is **rejected with 400**, not silently capped, so a client never receives fewer or
different items than it asked for. An invalid `page` or `size` (negative, zero size, non-numeric) is also
400 `application/problem+json` (ADR-0004).

Response shape: `{ "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`.
A page past the end returns 200 with empty `content`.

`GET /api/v1/wallets` lists only the caller's wallets, ordered by creation time then wallet id.
