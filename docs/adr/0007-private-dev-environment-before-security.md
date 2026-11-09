# ADR-0007: Keep the dev environment private until the Security epic lands

- Status: accepted
- Date: 2026-11-09

## Context
[ADR-0006](0006-temporary-owner-identity-header.md) identifies the caller from the `X-User-Id` header. That header is
not secure (anyone can claim any id), and the ADR says it must not be exposed beyond local and test use until real
authentication replaces it. Swagger UI (VPAY-30) has the same property: it documents and lets callers invoke every
endpoint, including the unprotected admin freeze/unfreeze endpoint.

Phase 2 deploys the service to an AWS dev environment (VPAY-34 Terraform, VPAY-35 CD) **before** the Security epic
(VPAY-7) delivers authentication. Without a decision, a deployed dev environment would put the unsecured identity and
Swagger UI on the public internet, against ADR-0006.

## Options considered
1. **Private or IP-restricted dev environment, auth deferred to VPAY-7.** Restrict access to the owner's IP, or keep
   the service on a private network reached through a tunnel or SSM port forwarding. No code change; the deployed
   service behaves like local. Cost: the environment cannot be shared or demoed publicly, and the restriction must be
   maintained (for example when the owner's IP changes).
2. **Pull a minimal auth story forward** (for example a static API key or a basic JWT check) so the environment can be
   public. Cost: throwaway auth work that VPAY-7 (Keycloak, JWT, RBAC) would replace, a second identity mechanism next
   to `CurrentOwner`, and a risk that "minimal" auth is mistaken for real protection of a money-handling service.
3. **Public environment with `X-User-Id` and Swagger UI left open.** Rejected: contradicts ADR-0006 and lets anyone
   act as any wallet owner.

## Decision
Option 1. The deployed dev environment stays **private**: reachable only from the owner's IP, or only inside a private
network via tunnel/SSM. Authentication is deferred to VPAY-7.

- The restriction covers the whole service, including `X-User-Id`-protected endpoints, the actuator endpoints that are
  not needed publicly, and **Swagger UI and `/v3/api-docs`** (VPAY-30). They are not exposed separately and need no
  separate protection while the environment is private.
- No load balancer, public IP or DNS record that accepts traffic from arbitrary sources may be created for the service
  by VPAY-34. Where a health check needs reachability (VPAY-35), it runs from inside the allowed network or from an
  allowed source.

### Exit condition
The restriction may be removed, or replaced by real authentication, only when **both** hold:
- VPAY-7 has replaced `HeaderCurrentOwner` with real authentication (a new `CurrentOwner` implementation per ADR-0006),
  and
- Swagger UI is switched off or protected in the environment being opened.

No environment that is not private (staging, production, or a public demo) goes live before that. Opening the dev
environment itself follows the same rule.

## Consequences
- VPAY-34 and VPAY-35 are unblocked and must design network access around this decision (restricted security group or
  private subnet; no public ingress).
- VPAY-30 may expose Swagger UI in the deployed environment because the environment is private; it must not do so in
  any environment that is not.
- The dev environment cannot be shown to others by URL until VPAY-7 is done.
- Someone must keep the allow-list current (owner's IP) or the tunnel working; a stale rule fails closed.
- Revisit this ADR when VPAY-7 is planned, and supersede it with a new ADR when the exit condition is met.
