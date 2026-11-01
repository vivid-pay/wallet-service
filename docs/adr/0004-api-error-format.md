# ADR-0004: API errors as RFC 9457 problem details

- Status: accepted
- Date: 2026-11-01

## Context
Clients need to handle every failure the same way. Without a convention each controller would invent its own error body, and Spring's defaults leak internals (exception messages, stack traces) in some paths.

## Options considered
1. Per-controller error bodies – flexible, but inconsistent and easy to leak internals.
2. Custom error envelope – consistent, but a bespoke format every client must learn.
3. RFC 9457 `application/problem+json` through one `@RestControllerAdvice` – a standard format, supported natively by Spring's `ProblemDetail`.

## Decision
Option 3. `com.sithum.wallet.web.ApiExceptionHandler` is the only place exceptions become HTTP responses; domain modules throw domain exceptions and never see HTTP.

- Body: `type`, `title`, `status`, `detail`, `instance` plus a `correlationId` extension member.
- Validation failures: 400 with an `errors` list of `{field, message}`.
- Unknown routes: 404. Unhandled exceptions: 500 with a fixed generic detail; the cause is logged, never returned.
- `CorrelationIdFilter` assigns every request an ID (the caller's `X-Correlation-Id` if it matches `[A-Za-z0-9._-]{1,64}`, otherwise a UUID), puts it in the logging MDC, echoes it in the `X-Correlation-Id` response header and the body, and the log pattern prints it.

## Consequences
Clients get one error shape and can quote the correlation ID to support. New domain exceptions need an explicit mapping in the handler (until then they are 500). Errors raised before the servlet dispatch (e.g. by the container) are not covered.
