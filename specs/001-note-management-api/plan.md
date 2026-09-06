# Implementation Plan: Quick Note — Note Management API

**Branch**: `001-note-management-api` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-note-management-api/spec.md`

## Summary

Deliver a personal note-management REST API: an authenticated user captures notes (title, body,
color), organizes them with pin/archive state and user-defined labels, finds them by
case-insensitive text search and state/label filters, and recovers deleted notes from a 30-day
trash. All 43 functional requirements in the spec are in scope.

The technical approach is a single Quarkus service on Java 25, exposing a hand-authored OpenAPI 3.1
contract implemented behind a four-layer package structure (api → application → domain →
infrastructure) with the dependency direction enforced by an automated architecture test. Identity
is verified, not issued: Keycloak is the OIDC provider and the service validates bearer JWTs and
derives the acting user from the `sub` claim. Persistence is PostgreSQL through Hibernate ORM with
Panache repositories confined to the infrastructure layer. Local development and integration testing
provision PostgreSQL and Keycloak through Quarkus Dev Services (Testcontainers); Docker Compose
provides the equivalent stack for running the packaged container. Development follows the
constitution's mandatory TDD cycle, with contract conformance asserted against the authored OpenAPI
document rather than against a document generated from the code.

## Technical Context

**Language/Version**: Java 25 (LTS), release-flag `--release 25`. Satisfies the requested "Java 21+";
Java 25 is chosen over 21 because Quarkus has had full Java 25 support since 3.31 and 25 is the
current LTS, so it maximizes the supported lifetime of the build.

**Primary Dependencies**: Quarkus 3.38.3 (`quarkus-rest`, `quarkus-rest-jackson`,
`quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-oidc`, `quarkus-flyway`,
`quarkus-hibernate-validator`, `quarkus-smallrye-health`, `quarkus-micrometer-registry-prometheus`,
`quarkus-logging-json`, `quarkus-opentelemetry`, `quarkus-quartz`, `quarkus-smallrye-openapi`).
Non-Quarkus: Bucket4j + Caffeine (rate limiting), ArchUnit (layer and structural-invariant
enforcement), swagger-request-validator (contract conformance), Gatling (load testing). Every
non-BOM dependency and plugin carries an explicit pinned version.

**Storage**: PostgreSQL 17, accessed via Hibernate ORM with Panache repositories. Schema evolution by
Flyway, forward-only, executed as a discrete deploy step.

**Testing**: JUnit 5, RestAssured, `@QuarkusTest` with Dev Services (Testcontainers-backed PostgreSQL
and Keycloak), AssertJ, ArchUnit, swagger-request-validator, Jacoco for coverage enforcement, and
Gatling (Java DSL, `gatling-maven-plugin`) for the concurrency load test under the `perf` profile.

**Target Platform**: Linux container (JVM base image). Native compilation available as an opt-in
Maven profile but not required for this feature.

**Project Type**: Web service — a single deployable REST API with no frontend in scope.

**Performance Goals**: p95 < 300 ms and p99 < 800 ms for read endpoints (constitution baseline);
first page of a 1,000-note collection returned in under 1 s at p95 (SC-002); 500 concurrent users at
those latencies (SC-006).

**Constraints**: Every endpoint authenticated and authorized per-resource; RFC 9457 error payloads;
all listings paginated (default 25, max 100); every query index-backed; explicit payload size limits
and request timeouts configured at the edge; no migrations at application start; no secrets or note
content in logs; JVM heap target under 512 MB per instance.

**Scale/Scope**: Design point of 10,000 users and up to ~5,000 notes per user. 15 endpoints across
notes, labels, note-label attachments, and trash. No unresolved NEEDS CLARIFICATION items — the
spec's single open question (auth scope) was resolved to verify-only before planning began.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` v1.0.0.

| Gate | Requirement | Design response | Status |
|------|-------------|-----------------|--------|
| I. Contract-First (NON-NEGOTIABLE) | OpenAPI 3.1 committed before handler code; response schema for **every** emitted status code; contract tests assert conformance | `contracts/openapi.yaml` hand-authored in this phase, before any Java exists. `swagger-request-validator` asserts every integration-test response against it. Every operation declares `413`, `500`, and `503` alongside its success and domain errors, so no emittable status is undeclared. `quarkus-smallrye-openapi` output is compared to the authored document in CI, and the authored document wins on divergence | PASS |
| II. Test-First (NON-NEGOTIABLE) | Failing test first; contract + integration + unit; 80% lines, 100% authn/authz/validation | Every task in `tasks.md` will be ordered test-then-implementation. Jacoco enforces 80% overall and 100% on the authentication (`security/`), authorization (`notes/application/`), and input-validation packages plus the invariant-holding domain classes; build fails below threshold | PASS |
| III. Layered architecture | Four layers, inward dependencies, domain free of framework/ORM/transport types | Package-per-layer inside one bounded context. Domain is plain Java records and interfaces. JPA entities live only in `infrastructure` and are mapped to domain objects at that boundary. Panache **active-record** is deliberately rejected in favour of Panache **repositories** for exactly this reason. ArchUnit test fails the build on any inward violation | PASS |
| IV. Secure by default | Authenticated unless declared public; authz at service layer; boundary validation; parameterized queries; idempotent writes; pinned deps; CI vuln scan | `quarkus.http.auth.permission.authenticated` denies by default; only `/q/health/*` is declared public with a written reason. Ownership checked in the application layer on every access, never inferred from the route. Bean Validation at the resource boundary with `FAIL_ON_UNKNOWN_PROPERTIES`. All access via Panache/JPQL parameter binding. `Idempotency-Key` supported on note creation. Dependencies pinned via BOM; OWASP Dependency-Check in CI fails on high/critical | PASS |
| V. Observability | Structured logs, correlation IDs, route-template metrics, real readiness, versioned forward-only migrations run as a discrete step, validated config | `quarkus-logging-json` + MDC correlation filter honouring `traceparent`/`X-Request-Id`. Micrometer HTTP metrics use URI templates. Readiness probe checks the datasource and OIDC issuer reachability. Flyway with `migrate-at-start=false` in prod, run as a separate container/step. Config validated at startup via `@ConfigMapping` with `@NotBlank` constraints | PASS |
| REST API Standards | Plural nouns, no verbs in paths, precise status codes, RFC 9457 errors with stable codes, mandatory pagination, URI versioning, rate limiting, index-backed queries, explicit payload limits and timeouts | All state changes are attribute updates (`PATCH /v1/notes/{id}`) or sub-resource nouns (`/v1/notes/{id}/labels/{labelId}`, `/v1/trash`). No verb appears in any path. Trigram GIN index backs search; composite index backs the pinned-first ordering. `quarkus.http.limits.*` and the read/idle timeouts are configured explicitly, with `413 payload_too_large` declared in the contract | PASS with one recorded deviation — see Complexity Tracking |
| Workflow & Quality Gates | Constitution Check in plan; six automated gates | This section is that check. All six gates are wired in the CI job defined in `quickstart.md` | PASS |

**Initial gate result**: PASS. One deviation recorded in Complexity Tracking; it has a stated removal
plan and does not require an amendment.

**Post-Phase-1 re-evaluation**: Re-run after `data-model.md` and `contracts/openapi.yaml` were
written. No new violations. Two design decisions were changed *because of* the constitution during
Phase 1 and are worth recording: (1) Panache active-record was replaced with the repository pattern
to keep ORM types out of the domain; (2) `POST /notes/{id}/restore` and `POST /notes/{id}/pin` were
replaced with attribute updates and a `/v1/trash` resource to eliminate verbs from paths. Result:
PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-note-management-api/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml     # Authoritative HTTP contract (OpenAPI 3.1)
│   └── error-catalog.md # Stable machine-readable error codes
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify output)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml                              # Quarkus BOM, pinned versions, Jacoco/ArchUnit/OWASP gates
docker-compose.yml                   # PostgreSQL + Keycloak + migration job + app
Dockerfile                           # JVM runtime image
.env.example                         # Documented, non-secret configuration template

src/main/java/dev/quicknote/
├── notes/                           # The single bounded context: notes, labels, attachments
│   ├── api/                         # Transport layer — nothing below imports this
│   │   ├── NoteResource.java
│   │   ├── LabelResource.java
│   │   ├── TrashResource.java
│   │   └── dto/                     # Request/response records; never reach the domain
│   ├── application/                 # Use cases, transaction boundaries, authorization
│   │   ├── NoteService.java
│   │   ├── LabelService.java
│   │   └── TrashService.java
│   ├── domain/                      # Plain Java. No Quarkus, no JPA, no Jakarta REST
│   │   ├── Note.java                # Entity with invariants (pin/archive/trash transitions)
│   │   ├── Label.java
│   │   ├── NoteColor.java           # Closed palette enum
│   │   ├── NoteState.java           # ACTIVE | ARCHIVED | TRASHED
│   │   ├── Page.java                # Pagination result, transport-agnostic
│   │   └── port/                    # Repository interfaces owned by the domain
│   │       ├── NoteRepository.java
│   │       └── LabelRepository.java
│   └── infrastructure/              # Adapters — JPA entities never leave this package
│       ├── entity/                  # NoteEntity, LabelEntity, NoteLabelEntity
│       ├── PanacheNoteRepository.java
│       ├── PanacheLabelRepository.java
│       └── mapper/                  # Entity ↔ domain mapping
├── security/                        # OIDC principal extraction; the only source of identity
│   ├── CurrentUser.java
│   └── CurrentUserProducer.java
├── shared/                          # Cross-cutting transport concerns
│   ├── problem/                     # RFC 9457 ProblemDetail + exception mappers
│   ├── pagination/                  # Page-parameter parsing and bounds enforcement
│   ├── idempotency/                 # Idempotency-Key storage and replay
│   └── correlation/                 # Correlation-ID filter and MDC wiring
└── platform/                        # Operational concerns
    ├── health/                      # Readiness checks (datasource, OIDC issuer)
    ├── ratelimit/                   # Bucket4j filter (per principal, per IP)
    └── scheduler/                   # Clustered trash-purge job

src/main/resources/
├── application.properties           # Profiles: dev (Dev Services), test, prod
└── db/migration/                    # Flyway V<YYYYMMDDHHmm>__*.sql forward-only migrations

src/test/java/dev/quicknote/
├── contract/                        # Responses validated against contracts/openapi.yaml
├── integration/                     # @QuarkusTest against real PostgreSQL + Keycloak
├── unit/                            # Domain invariants; no container, no database
├── architecture/                    # ArchUnit: layer, credential-scope, limit-agreement rules
├── perf/                            # Latency tests and Gatling simulations; `perf` profile only
└── support/                         # Shared harness: test base, tokens, contract validation
```

**Structure Decision**: Single-project web service, because the feature has no frontend and no second
deployable. Inside it, a single bounded context (`notes`) holds notes, labels, and their attachments
together, since the spec's rules couple them (label deletion detaches from notes, notes carry at most
20 labels) and splitting them would create a distributed transaction for no benefit. The four
constitution-mandated layers are expressed as packages inside that context rather than as Maven
modules — packages keep the build simple, and the dependency direction is enforced mechanically by
the ArchUnit test in `src/test/java/dev/quicknote/architecture/`, which fails the build rather than
relying on reviewer vigilance. `security`, `shared`, and `platform` sit outside the bounded context
because they serve it rather than belong to it.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Rate limiting is instance-local (Bucket4j + Caffeine), not cluster-wide, so the effective limit is `configured_limit × instance_count` | The constitution's REST API Standards require per-principal and per-IP rate limiting with `429` and `Retry-After`. An in-process limiter satisfies the behavioural contract and keeps the service dependency-free at this stage | A distributed limiter (Redis-backed Bucket4j) would be exact, but it adds a new runtime dependency, a new failure mode, and new operational surface for a service whose current design point is a small number of instances. **Removal plan**: when the service runs more than two instances, move enforcement to the API gateway or switch Bucket4j to a Redis backend; the filter interface is designed so only the backing store changes. Re-review at the next quarterly deviation review |
| Flyway runs at application start in the `dev` and `test` profiles | The constitution requires migrations to run as a discrete deploy step "rather than at application start". Dev Services creates a throwaway database per test run, and a separate migration step there would double test setup time for no safety gain | Running the discrete migration step in dev/test as well was rejected as pure cost: the constraint exists to prevent racing instances and unreviewed schema changes in production, neither of which applies to an ephemeral container. `migrate-at-start` is **false** in the `prod` profile, which is the case the rule protects, and CI verifies that setting |

### Deviation review — 2026-08-26

Both recorded deviations were re-reviewed at the end of implementation. Both still hold, and both
still have a live removal plan.

| Deviation | Status | Evidence |
|---|---|---|
| Instance-local rate limiting | **Still valid.** The service runs as a single instance today, so the configured ceiling is the effective one. The removal plan is intact: `RateLimiter` exposes one `check(principal, sourceIp)` method over a Caffeine store, so moving to a gateway or a shared store changes the backing store and nothing else — no filter and no caller is affected | `RateLimitTest` proves the behavioural contract (429, `Retry-After`, per-principal and per-IP buckets) independently of where the counters live |
| Flyway `migrate-at-start` in dev/test | **Still valid, and now demonstrated.** `prod` runs migrations as a discrete step: the Compose stack applies all six migrations in a one-shot `flyway` container that exits before the application container starts, and the application starts with `migrate-at-start=false` | Verified end to end on 2026-08-26 — `flyway` exited after applying six migrations, then the app started and answered `/q/health/ready` |

**One finding this review surfaced**, recorded because it is the kind of gap the review exists to
catch: the clustered Quartz job store needs its own tables, and nothing in the test suite could have
noticed they were missing — the `test` profile uses an in-memory store, so only the `prod` profile
touches that code path. The application failed to start in Compose until
`V202608260950__quartz_tables.sql` was added. The general lesson is worth keeping: a configuration
difference between profiles is a place where tests stop being evidence.

