# ADR-0006: Temporary caller identity from the X-User-Id header

- Status: accepted
- Date: 2026-11-07

## Context
Creating a wallet needs to know its owner, but authentication (Keycloak/JWT) belongs to the Security epic and
comes later. The endpoint must not hard-wire how identity is obtained, or the swap becomes a rewrite.

## Options considered
1. Read `X-User-Id` directly in controllers – quick, but every endpoint changes when JWT arrives.
2. Owner id in the request body – lets any caller act as anyone, and would change the API contract later.
3. One `CurrentOwner` interface with a header-based implementation – temporary, behind a single seam.

## Decision
Option 3. `accounts.CurrentOwner` is the public interface; `HeaderCurrentOwner` (in `accounts.internal`) reads
`X-User-Id` as a UUID. A missing or malformed header throws `UnauthenticatedException`, mapped to 401 problem+json
by `ApiExceptionHandler` (ADR-0004). The interface and exception sit in the module's base package so the web layer
can map the exception without a dependency cycle.

## Consequences
- Replacing the header with JWT is one new `CurrentOwner` implementation; controllers and services do not change.
- The header is **not secure**: anyone can claim any id. It must not be exposed beyond local and test use until the
  Security epic replaces it.
- Request validation runs before the controller body, so a request with both an invalid body and no header gets 400
  rather than 401.
