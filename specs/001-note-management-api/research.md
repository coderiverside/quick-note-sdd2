# Phase 0 Research: Quick Note — Note Management API

**Date**: 2026-08-25 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

All Technical Context entries are resolved; no NEEDS CLARIFICATION items remain. Each decision below
records what was chosen, why, and what was rejected.

---

## 1. Quarkus release stream

**Decision**: Quarkus **3.38.3** (latest release as of 2026-08-19), pinned exactly in `pom.xml` via
`quarkus-bom`.

**Rationale**: The stack was specified as "the latest Quarkus version". 3.38.x is the current release
stream and carries the newest Hibernate ORM, OIDC, and Dev Services fixes.

**Alternatives considered**: **Quarkus 3.33 LTS** — the newest LTS stream, which receives backported
fixes for a guaranteed window and is the conventional choice for a long-lived production API.
Recommend switching to it if this service will be maintained by a team that does not want to track a
monthly release cadence; the change is a single `quarkus.platform.version` property and no code is
affected. **Quarkus 3.27 LTS** — older LTS, rejected as unnecessarily behind. This is the one
decision here where the "latest" instruction and standard practice for a professional API pull in
different directions, so it is flagged rather than buried.

---

## 2. Java version

**Decision**: **Java 25** (LTS), compiled with `maven.compiler.release=25`.

**Rationale**: The requirement was "Java 21+". Quarkus has had full Java 25 support — including
runtime images and Mandrel native builds — since 3.31, so 3.38 is comfortably within support. Java 25
is the current LTS, which gives the build the longest supported life for the same effort. Records,
sealed interfaces, and pattern matching are used throughout the domain layer.

**Alternatives considered**: Java 21 — also fine and explicitly named in the request, but it buys
nothing over 25 and retires sooner.

---

## 3. HTTP layer and concurrency model

**Decision**: `quarkus-rest` (Quarkus REST, the Jakarta REST implementation formerly called RESTEasy
Reactive) with **blocking endpoints annotated `@RunOnVirtualThread`**.

**Rationale**: Every operation in this feature is a short database round-trip. Virtual threads give
the throughput profile of the reactive stack while letting handlers, services, and Hibernate calls
stay in ordinary imperative code with `@Transactional` semantics — which matters because the
constitution requires the application layer to own transaction boundaries, and reactive Hibernate
would push that into a different programming model for no measurable gain at this scale.

**Alternatives considered**: **Hibernate Reactive + Mutiny** — highest theoretical throughput,
rejected because it complicates transactions, testing, and stack traces for a workload that is not
connection-bound. **Classic blocking worker pool** — simplest, but caps concurrency at the worker
pool size under the 500-concurrent-user target in SC-006.

---

## 4. Persistence pattern

**Decision**: **Hibernate ORM with Panache repositories** (`PanacheRepositoryBase<NoteEntity, UUID>`),
with JPA entities confined to `notes/infrastructure/entity/` and mapped to plain-Java domain objects
at that boundary. Repository *interfaces* are declared in `notes/domain/port/` and implemented in
infrastructure.

**Rationale**: Constitution Principle III forbids ORM types in the domain layer and forbids
persistence types crossing out of infrastructure. Panache's active-record style — entities extending
`PanacheEntity` with static finders — puts the ORM directly into the model and would violate both
rules. The repository flavour of Panache keeps the query ergonomics (`find("owner = ?1 and state = ?2",
…)`, `page()`, `list()`) while satisfying the layering rule.

**Cost accepted**: an explicit entity↔domain mapper per aggregate. This is real boilerplate. It is
accepted because the constitution mandates the layering, and because it is what makes the domain
invariants (pin/archive/trash transitions) unit-testable without a database.

**Alternatives considered**: **Panache active record** — less code, rejected as a direct Principle III
violation that would have required a constitutional amendment rather than a design choice. **Plain
JPA `EntityManager`** — compliant but gives up Panache's paging and query helpers for nothing.
**jOOQ / plain SQL** — good fit for the search query, rejected as a second persistence idiom to
maintain alongside JPA.

---

## 5. Identity and authentication

**Decision**: `quarkus-oidc` in **`service` application type** (bearer-token validation only).
Keycloak is the issuer. The service validates signature, `iss`, `aud`, and `exp` against the
provider's JWKS, and derives the acting user from the **`sub` claim** injected as
`SecurityIdentity`/`JsonWebToken`. `quarkus.http.auth.permission` denies unauthenticated access
globally; only `/q/health/live` and `/q/health/ready` are declared public.

**Rationale**: The spec was resolved to *verify-only* scope — this service never issues, refreshes, or
revokes credentials and stores no passwords. `service` type does exactly that and nothing more; the
`web-app` type would add authorization-code flow and session cookies this service must not own.

**User identity storage**: no local `users` table. `sub` is stored as the owner column on notes and
labels. A first-time user needs no provisioning step, which satisfies the spec's edge case that a
validly signed credential for an unknown user yields an empty collection rather than an error.

**Alternatives considered**: **`quarkus-smallrye-jwt`** — lighter, validates JWTs without OIDC
discovery, rejected because it gives up automatic JWKS rotation and Dev Services integration.
**Local user mirror table** — rejected as duplicated state with no current reader; `sub` is a stable
opaque identifier and is sufficient.

---

## 6. Local development and test infrastructure

**Decision**: **Quarkus Dev Services** for both PostgreSQL and Keycloak in `dev` and `test` profiles
(`quarkus-test-keycloak-server` provides a Keycloak container with a seeded realm and test users).
**Docker Compose** provides the equivalent stack for running the packaged image and for anyone who
wants the services up independently of the build.

**Rationale**: Dev Services means `mvn quarkus:dev` and `mvn verify` need no manual setup and no
checked-in credentials, and every integration test runs against a real PostgreSQL and a real
Keycloak rather than mocks — which is what the constitution's integration-test requirement asks for.
Compose covers the case Dev Services does not: running the built container.

**Alternatives considered**: **H2/in-memory database for tests** — rejected outright; the search
implementation depends on PostgreSQL trigram indexes and `ILIKE` semantics, so an in-memory database
would test something the service does not do. **Hand-managed Testcontainers** — Dev Services already
wraps Testcontainers and shares containers across the run.

---

## 7. Schema migrations

**Decision**: **Flyway** with versioned, forward-only SQL in `src/main/resources/db/migration/`.
`quarkus.flyway.migrate-at-start` is **false** in `prod` and true in `dev`/`test`. Production
migration runs as a discrete step — a dedicated `flyway` service in Compose, and the equivalent job
in any deployment pipeline — before the application container starts.

**Rationale**: Constitution Principle V requires versioned, forward-only migrations run as a discrete
deploy step. Flyway's SQL-first model keeps the migration reviewable as SQL. The dev/test exception is
recorded in the plan's Complexity Tracking with its justification.

**Alternatives considered**: **Liquibase** — more abstraction (XML/YAML changelogs) than this schema
needs. **Hibernate `schema-generation`** — rejected; not reviewable, not versioned, and prohibited by
the discrete-step rule.

---

## 8. Error representation

**Decision**: Hand-rolled **RFC 9457 Problem Details**: a `ProblemDetail` record serialized as
`application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, a stable
machine-readable `code`, and — for validation failures — an `errors` array of
`{field, code, message}`. Delivered by a small set of `ExceptionMapper`s over a sealed
`DomainException` hierarchy. Codes are catalogued in
[contracts/error-catalog.md](./contracts/error-catalog.md).

**Rationale**: The constitution requires RFC 9457 *plus* a stable `code` and a per-field `errors`
array, and declares those codes part of the contract. Writing ~60 lines of mapper gives exact control
over that shape and over the rule that no internal detail leaks. A generic 500 mapper logs the
correlation ID and returns an opaque body.

**Alternatives considered**: **`quarkus-resteasy-problem`** — a community extension that produces
problem+json automatically, rejected because bending it to the required `code`/`errors` shape is more
work than writing the mappers, and it adds a dependency outside the Quarkus platform BOM.

---

## 9. Contract-first workflow

**Decision**: `contracts/openapi.yaml` is **hand-authored and authoritative**, written before any Java
code exists. Two mechanisms keep code and contract aligned:
1. **Conformance**: `swagger-request-validator-restassured` validates every integration-test request
   and response against `openapi.yaml`. A response shape absent from the contract fails the test.
2. **Drift**: CI exports the `quarkus-smallrye-openapi` document from the running app and compares it
   to the authored file; on divergence the authored file is correct and the code is the defect.

**Rationale**: Constitution Principle I is explicit that the contract precedes the implementation. A
code-generated OpenAPI document inverts that — it describes whatever was built, including mistakes —
so it is used only as a drift detector, never as the source of truth.

**Alternatives considered**: **Generate the contract from annotations** — rejected as a direct
inversion of Principle I. **Generate server stubs from the YAML** — rejected as awkward against
Quarkus build-time indexing and unnecessary once conformance is asserted by tests.

---

## 10. Pagination strategy

**Decision**: **Offset/limit pagination** — `page` (0-based) and `size` (default 25, max 100) —
returning items plus `page`, `size`, `totalElements`, `totalPages`. Requests above the maximum are
rejected `400`, not silently clamped.

**Rationale**: The default ordering is pinned-first then most-recently-updated-first, and both keys
mutate frequently, so keyset pagination would need a composite cursor over `(pinned, updated_at, id)`
that invalidates as soon as a user pins a note mid-scroll. At the design point of ~5,000 notes per
user, offset pagination against a per-owner composite index stays well inside the p95 budget. The
constitution's rule that the simpler compliant design wins applies directly.

**Migration path**: if per-user collections exceed roughly 10,000 notes, or deep-page latency is
observed above budget, switch to a keyset cursor. The response envelope already carries a `nextPage`
link, so clients that follow it need no change.

**Alternatives considered**: **Keyset/cursor** — correct under concurrent mutation and O(1) at depth,
rejected now as premature for the stated scale. **Unbounded lists** — prohibited by the constitution.

---

## 11. Search implementation

**Decision**: Case-insensitive substring match with `ILIKE '%term%'` across `title` and `body`,
backed by a **`pg_trgm` GIN index** on both columns. The search term is escaped for `%`, `_`, and `\`
before binding, and bound as a parameter.

**Rationale**: The spec defines search as case-insensitive substring matching and explicitly excludes
stemming, fuzzy matching, and relevance ranking. A leading-wildcard `ILIKE` cannot use a B-tree index
at all, so without trigram indexing this query would be a sequential scan — which the constitution
prohibits ("every query MUST be supported by an index appropriate to its access path"). `pg_trgm` GIN
is the access path that makes `%term%` indexable.

**Alternatives considered**: **PostgreSQL full-text search (`tsvector`/`GIN`)** — faster and rank-
aware, but it matches whole lexemes, so searching "note" would miss "notebook" and contradict the
spec's substring semantics. Reconsider if the spec's search definition ever changes. **External search
engine** — vastly disproportionate.

---

## 12. Trash retention job

**Decision**: **`quarkus-quartz` with a clustered JDBC job store** running a daily job that
permanently deletes notes trashed more than 30 days ago, in bounded batches.

**Rationale**: Retention must happen without user action (FR-024). With more than one instance
deployed, a plain `@Scheduled` method fires on every instance simultaneously; Quartz's clustered
store guarantees exactly one execution. Batching bounds the transaction and prevents a long lock on
the notes table.

**Alternatives considered**: **`@Scheduled`** — simplest, rejected because it is incorrect under
horizontal scaling. **PostgreSQL advisory lock around `@Scheduled`** — workable and lighter than
Quartz, but reimplements what the clustered store already provides. **`pg_cron`** — moves business
rules into the database, away from the domain layer and its tests.

---

## 13. Write idempotency

**Decision**: `Idempotency-Key` header supported on `POST /v1/notes`. The key, scoped to the owner, is
stored with the resulting note identifier and response status; a replay within a 24-hour window
returns the original response. Every other mutating operation is naturally idempotent by design —
state changes are `PATCH` attribute writes, label attach/detach are `PUT`/`DELETE` on a sub-resource,
and trashing is `DELETE`.

**Rationale**: The constitution requires write operations that create resources to be idempotent or
protected by an idempotency key. `POST /v1/notes` is the only non-idempotent operation in the
contract, so the key applies there alone.

**Alternatives considered**: **Client-supplied note UUID via `PUT /v1/notes/{id}`** — makes creation
idempotent with no extra table, rejected because it exposes identifier generation to clients and
complicates the "create" story in the API. **No idempotency** — a constitutional violation.

---

## 14. Rate limiting

**Decision**: **Bucket4j** with a Caffeine-backed store, applied in a Jakarta REST
`ContainerRequestFilter` — one bucket per authenticated principal and one per source IP, whichever is
exhausted first. Exceeding either returns `429` with `Retry-After` and `RateLimit-*` headers.

**Rationale**: The constitution requires per-principal and per-IP limiting with `429` and
`Retry-After`. Bucket4j implements token buckets correctly, and Caffeine bounds memory.

**Known limitation**: enforcement is instance-local, so the effective ceiling scales with instance
count. This is recorded as a deviation in the plan's Complexity Tracking, with the removal plan
(gateway enforcement or a Redis-backed Bucket4j store) and a re-review trigger.

---

## 15. Concurrent edits

**Decision**: JPA **optimistic locking** via a `@Version` column on the note entity. A losing write
returns `409` with the `note_version_conflict` code.

**Rationale**: The spec's concurrency edge case requires the collection to stay internally consistent
and the last-updated timestamp to reflect the surviving state. Optimistic locking is the standard
answer for low-contention personal data and costs one integer column.

**Alternatives considered**: **Last-write-wins** — simpler, but silently discards a concurrent edit,
which is data loss the user cannot detect. **Pessimistic row locks** — unnecessary contention cost for
a single-user-per-collection access pattern.

---

## 16. Case-insensitive label uniqueness

**Decision**: A **unique index on `(owner_id, lower(trim(name)))`** in PostgreSQL, with the trimmed
original casing preserved in the stored `name` column for display.

**Rationale**: FR-027 requires per-user uniqueness compared case-insensitively and ignoring
surrounding whitespace, while the user's chosen casing must survive round-trip (FR-042). A functional
unique index enforces the rule in the database, so a race between two concurrent creates cannot
produce a duplicate — an application-level check alone cannot guarantee that.

**Alternatives considered**: **`citext` column type** — equivalent, rejected because it requires an
extension for behaviour a functional index already provides and it does not handle the trim.
**Application-only check** — subject to a check-then-insert race.

---

## 17. Observability wiring

**Decision**: `quarkus-logging-json` for structured logs; a correlation filter that accepts an inbound
`traceparent` or `X-Request-Id`, generates one when absent, places it in the MDC, and echoes it on the
response; `quarkus-micrometer-registry-prometheus` for HTTP metrics labelled by **URI template**;
`quarkus-smallrye-health` with a readiness check covering the datasource and OIDC issuer reachability;
`quarkus-opentelemetry` for trace propagation.

**Rationale**: These are the exact affordances Principle V requires. Templated metric labels are
called out explicitly because labelling by concrete path (`/v1/notes/{uuid}`) would produce unbounded
cardinality — the failure mode the constitution names.

**Log content rule**: note titles, bodies, label names, and tokens are never logged at any level. Log
statements carry the note identifier, the owner `sub`, and the correlation ID only. This is enforced
by review and by a unit test over the log-message constants.

---

## 18. Containerization

**Decision**: JVM container from the Quarkus `ubi9/openjdk-25-runtime` base with the fast-jar layout,
running as a non-root user. `docker-compose.yml` provisions PostgreSQL 17, Keycloak with an imported
realm, a one-shot Flyway migration service, and the application, with the app depending on the
migration service completing successfully.

**Rationale**: The fast-jar layout gives sub-second JVM startup, which covers the "fast startup"
requirement without the build-time cost of native compilation. Native remains available behind a
Maven profile if a deployment target ever needs sub-100 ms cold start.

**Alternatives considered**: **Native image by default** — best startup and memory, rejected as the
default because it lengthens the build materially and constrains reflection-using libraries, for a
service that is long-running rather than scale-to-zero.

---

## Resolved unknowns summary

| Unknown from Technical Context | Resolution |
|---|---|
| Exact Quarkus version | 3.38.3, pinned; 3.33 LTS documented as the conservative alternative |
| Java version within "21+" | Java 25 LTS |
| Panache style compatible with Principle III | Repository pattern, entities confined to infrastructure |
| Contract-first mechanics under Quarkus | Authored YAML authoritative; generated document used only for drift detection |
| Indexable substring search | `pg_trgm` GIN on title and body |
| Pagination style | Offset/limit, with a documented keyset migration path |
| Single-execution scheduled purge | Quartz clustered JDBC job store |
| Rate limiting scope | Instance-local, recorded as a deviation with a removal plan |
