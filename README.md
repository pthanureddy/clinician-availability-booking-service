# Clinician Availability and Booking Service

An independent Java 17 / Spring Boot and React portfolio application for clinician
slot availability and synthetic patient bookings in SE, NO, FR and UK. It is not
affiliated with Kry and contains no real patient or clinician data.

## Behavior

- Match slots by market, care specialty and clinician language.
- Reject overlapping bookings for either clinician or patient, including distinct
  slot IDs that overlap. Adjacent appointments use half-open time intervals.
- Serialize mutations through a database row lock; booking, rebooking,
  cancellation and idempotency records share one transaction.
- Failed rebooking preserves the original appointment. Cancellation releases capacity.
- Repeating a mutation key with the same request performs no extra write; changed
  facts return HTTP 409. A replay returns the booking's **current state**, rather
  than a frozen original response.
- Display matching slots, book, move and cancel from a React interface with
  disabled pending actions and visible server error messages.

## Architecture and choices

React -> REST controller -> transactional scheduling service -> JDBC database.
Bean Validation protects booking payloads; domain errors use ProblemDetail.
The service stores UTC-offset timestamps and the browser displays local time.
The default H2 PostgreSQL compatibility mode makes the test setup self-contained.
A PostgreSQL JDBC driver and environment configuration are included, but the
PostgreSQL path has not been integration-tested or deployed.

The global lock favors a simple correctness argument over throughput. All service
instances sharing one database use the same mutex row. It is not an in-process
Java lock. A larger system would partition locks by clinician/patient, use ordered
locking and retry deadlocks, add database constraints and durable domain events.

## Setup, test and run

Requires Java 17+, Node.js 22.12+ and network access for Maven/npm dependencies.

```powershell
.\mvnw.cmd --batch-mode clean verify
java -jar target/clinician-availability-booking-service-1.0.0.jar
# In a second terminal:
cd web
npm ci
npm test
npm run build
npm run dev
```

On Linux/macOS, replace `.\mvnw.cmd` with `./mvnw` (run `chmod +x mvnw` first).
Open http://localhost:5173. Vite proxies `/api` to port 8080.
Health: http://localhost:8080/actuator/health.

Default fixtures use fixed 2 October 2026 appointments for repeatable tests. They
remain queryable after that date; no current-date eligibility is implied.
SE has two adjacent English GP appointments; NO and UK have one each; FR has
one French GP appointment. Change the language filter to `fr` to view it.

## HTTP example

```bash
curl 'http://localhost:8080/api/slots?market=SE&specialty=GP&language=en'
curl -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: booking-001' \
  -d '{"slot":"se-1","need":{"patient":"synthetic-patient","market":"SE","specialty":"GP","language":"en"}}'
```

The result contains a generated `id`, `slot: se-1` and `active: true`. Reuse that
ID in `POST /api/bookings/{id}/rebook` with `{"slot":"se-2"}` or in
`POST /api/bookings/{id}/cancel` with `{}`. Use a fresh Idempotency-Key for each
different mutation. GET `/api/bookings` lists synthetic bookings.

## Verification

`./mvnw clean verify` exercises matching, overlapping clinicians and patients,
adjacent appointments, retries and changed-key conflicts, successful and failed
rebooking, cancellation, a synchronized two-request race and HTTP validation.
`npm test` verifies frontend selection, request facts, market changes, empty
availability and visible errors. `npm run build` produces the web bundle.
GitHub Actions repeats backend/frontend verification and builds the Java container.

## Limitations and deployment boundary

This is portfolio engineering evidence, not a healthcare product. Authentication,
authorization, clinical triage, consent, real patient records, timezone/DST schedule
generation, schedule editing, capacity balancing, waitlists, notifications, audit
retention, load tests and production operation are outside scope. Booking listing
is intentionally unrestricted for local synthetic use. The UI generates a new
mutation key per click and does not retry automatically; API clients can retain
keys for safe network retries. Availability is a snapshot; mutations recheck it.

H2 state is lost on restart. Schema initialization is intended for a single startup
process, not concurrent migration. Configure `DATABASE_URL`, `DATABASE_USER` and
`DATABASE_PASSWORD` for a different database; adopt migrations before deployment.
The Dockerfile packages the backend only; frontend hosting is separate. No AWS
resources were provisioned. See [AI-assisted development](docs/AI_ASSISTED_DEVELOPMENT.md).
