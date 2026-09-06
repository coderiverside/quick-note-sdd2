<!--
SYNC IMPACT REPORT
==================
Version change: (unversioned template scaffold) → 1.0.0
Bump rationale: MAJOR — initial ratification. All placeholder tokens replaced with
binding governance content; no prior ratified version existed.

Modified principles:
  - [PRINCIPLE_1_NAME] → I. Contract-First API Design (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Test-First Delivery (NON-NEGOTIABLE)
  - [PRINCIPLE_3_NAME] → III. Layered Architecture and Dependency Discipline
  - [PRINCIPLE_4_NAME] → IV. Secure and Correct by Default
  - [PRINCIPLE_5_NAME] → V. Observability and Operational Readiness

Added sections:
  - REST API Standards (replaces [SECTION_2_NAME])
  - Development Workflow and Quality Gates (replaces [SECTION_3_NAME])
  - Governance (populated)

Removed sections: none

Deferred TODOs:
  - TODO(PROJECT_NAME): Project name inferred from the working directory
    ("quick-note"). Confirm the official service name and amend the title if it
    differs. This is a PATCH-level amendment when corrected.
-->

# Quick Note API Constitution

## Core Principles

### I. Contract-First API Design (NON-NEGOTIABLE)

The HTTP contract is the product; the implementation is an artifact of it.

- Every endpoint MUST be described in a versioned OpenAPI 3.1 document committed to the
  repository before any handler code is written.
- The specification MUST define, per operation: request schema, response schema for every
  emitted status code, error schema, authentication requirements, and pagination behavior.
- Generated or hand-written contract tests MUST assert that live responses conform to the
  published schema. A response shape that is not in the contract is a defect, not a feature.
- Breaking contract changes (removing a field, narrowing a type, changing status semantics,
  making an optional input required) MUST NOT ship into an existing API version. They require
  a new version and a documented deprecation window.

**Rationale**: Consumers integrate against the contract, not the code. Writing the contract
first forces design decisions to be resolved while they are still cheap, and makes drift
mechanically detectable rather than a matter of reviewer vigilance.

### II. Test-First Delivery (NON-NEGOTIABLE)

- Tests MUST be written and MUST fail before the implementing code exists. Red → Green →
  Refactor is enforced per change, not per milestone.
- Every endpoint MUST carry: contract tests (schema conformance), integration tests against a
  real database and real HTTP stack, and unit tests for domain logic with non-trivial branching.
- Every bug fix MUST begin with a regression test that reproduces the defect and fails.
- Tests MUST be deterministic and independent: no shared mutable fixtures between tests, no
  reliance on execution order, no unpinned wall-clock or network dependencies.
- Coverage MUST be at least 80% of lines overall and 100% of authentication, authorization,
  and input-validation paths. Coverage is a floor, never the goal.

**Rationale**: An API is a long-lived public commitment. Tests written after the fact encode
the behavior that was built, including its mistakes; tests written first encode the behavior
that was intended.

### III. Layered Architecture and Dependency Discipline

- Code MUST be organized into four layers with a strictly inward dependency direction:
  transport (routing, serialization, HTTP concerns) → application/service (use cases,
  orchestration, transactions) → domain (entities, invariants, business rules) → infrastructure
  (persistence, external clients, messaging).
- The domain layer MUST NOT import a web framework, an ORM, or any transport type. Domain rules
  MUST be testable without starting a server or a database.
- HTTP types (request, response, status codes) MUST NOT cross out of the transport layer.
  Persistence types (ORM models, row records) MUST NOT cross out of the infrastructure layer.
  Cross-layer transfer happens through explicit DTOs or domain objects.
- All external I/O — database, cache, third-party APIs — MUST be reached through an interface
  owned by the inner layer, so it can be substituted in tests.
- Each dependency added to the project MUST be justified in the plan: what it does, what it
  replaces, and why writing it is worse than importing it.

**Rationale**: Layering is what makes a service survive a framework upgrade, a database change,
or a rewrite of its transport. Enforcing the dependency direction is what makes layering real
rather than decorative folder names.

### IV. Secure and Correct by Default

- Every endpoint MUST be authenticated unless it is explicitly declared public in the
  specification, with a written reason. There is no implicit public endpoint.
- Authorization MUST be enforced at the service layer against the acting principal, not
  inferred from the route. Every resource access MUST verify ownership or an explicit grant.
- All inbound data — body, query, path, header — MUST be validated against a declared schema at
  the boundary and rejected with 400 or 422 before reaching domain logic. Unknown fields MUST be
  rejected or explicitly ignored by declared policy, never silently persisted.
- Secrets MUST come from environment or a secret manager. Credentials, tokens, and keys MUST NOT
  appear in source, logs, error messages, or test fixtures.
- Responses MUST NOT leak internals: no stack traces, SQL fragments, hostnames, or upstream
  error text in a client-facing payload.
- All database access MUST use parameterized queries. String-concatenated SQL is prohibited.
- Write operations that create resources or transfer value MUST be idempotent or protected by an
  idempotency key.
- Dependencies MUST be pinned, and a vulnerability scan MUST run in CI. A known high or critical
  advisory blocks release.

**Rationale**: Security defects in an HTTP API are directly reachable by anyone on the network.
Defaults decide outcomes: a system that is closed until deliberately opened fails safe, while one
that is open until deliberately closed fails silently.

### V. Observability and Operational Readiness

- Logs MUST be structured (JSON), MUST include a request-scoped correlation ID propagated from
  and to upstream services, and MUST NOT contain secrets or personal data beyond declared,
  minimized fields.
- Every request MUST emit latency, status code, and route-template metrics. Route labels MUST use
  the template, not the concrete path, to avoid unbounded cardinality.
- The service MUST expose a liveness endpoint and a readiness endpoint that actually verifies
  critical dependencies.
- Every unhandled error MUST be logged with the correlation ID and returned to the client as a
  generic, contract-conformant error payload.
- Database migrations MUST be versioned, forward-only, reversible or explicitly documented as
  irreversible, and MUST run as a discrete deploy step rather than at application start.
- Configuration MUST come from the environment. Config MUST be validated at startup, and the
  process MUST refuse to start on invalid or missing required values.

**Rationale**: A service that cannot be diagnosed in production has not been finished. These are
the minimum affordances required to answer "what is failing, for whom, and since when" without
attaching a debugger to a live system.

## REST API Standards

These standards are binding on every specification and implementation.

**Resources and URIs**
- Paths MUST name plural noun collections (`/v1/notes`, `/v1/notes/{noteId}`). Verbs in paths are
  prohibited; the HTTP method is the verb.
- Path segments MUST be lowercase kebab-case. JSON field names MUST be consistent project-wide;
  the chosen convention is `camelCase` and MUST NOT vary per endpoint.
- Nesting MUST NOT exceed two resource levels. Deeper relationships MUST be expressed by query
  filters on the top-level collection.

**HTTP semantics**
- GET, HEAD, PUT, and DELETE MUST be idempotent; GET and HEAD MUST be side-effect free.
- Status codes MUST be used precisely: 200 retrieval/update, 201 with `Location` on creation,
  202 accepted async, 204 empty success, 400 malformed, 401 unauthenticated, 403 unauthorized,
  404 absent, 409 state conflict, 422 semantically invalid, 429 rate limited, 5xx server fault.
- A 2xx MUST NOT be returned for a failed operation under any circumstance.

**Errors**
- Error responses MUST follow RFC 9457 Problem Details (`type`, `title`, `status`, `detail`,
  `instance`) plus a stable machine-readable `code` and, for validation failures, a per-field
  `errors` array.
- Error `code` values are part of the contract and MUST NOT change meaning without a version bump.

**Collections**
- Every collection endpoint MUST be paginated with a documented default and an enforced maximum
  page size. Unbounded list responses are prohibited.
- Filtering, sorting, and field selection MUST be explicit, allow-listed query parameters.

**Versioning and evolution**
- The API version MUST appear in the URI path (`/v1/`). Additive, optional changes are permitted
  within a version; anything else is not.
- Deprecated endpoints MUST return a `Deprecation` header and remain available for at least one
  documented deprecation window before removal.

**Performance and protection**
- Baseline budgets: p95 latency under 300 ms and p99 under 800 ms for read endpoints at expected
  load, measured excluding cold start. A feature specification MAY tighten these; it MUST NOT
  loosen them without a recorded amendment.
- Every query behind an endpoint MUST be supported by an index appropriate to its access path.
  N+1 query patterns are defects.
- Rate limiting MUST be applied per principal and per IP, and MUST respond 429 with `Retry-After`.
- Payload size limits and request timeouts MUST be configured explicitly at the edge.

## Development Workflow and Quality Gates

**Specification and planning**
- Work begins with a specification. A specification MUST state user-facing behavior, the resource
  contract, error cases, authorization rules, and acceptance criteria. Ambiguities MUST be
  resolved before planning, not during implementation.
- Every plan MUST include a Constitution Check. Any deviation MUST be recorded with the principle
  in tension, why the simpler compliant approach was rejected, and the removal plan.

**Change management**
- All changes land through pull requests. Direct pushes to the main branch are prohibited.
- Commits MUST follow Conventional Commits. Each PR MUST be scoped to one coherent change.
- A PR MUST NOT be merged while CI is failing or while it is missing tests for changed behavior.

**Automated gates (all MUST pass before merge)**
1. Lint and formatting checks with zero warnings.
2. Static type checking with zero errors.
3. Full test suite green, coverage thresholds from Principle II met.
4. OpenAPI document validates, and contract tests conform to it.
5. Dependency vulnerability scan clean of high and critical advisories.
6. Migrations apply cleanly against a fresh database and against the current production schema.

**Review**
- Every PR requires at least one approving review from someone other than the author.
- Reviewers MUST verify constitutional compliance, not only correctness, and MUST cite the
  specific principle when requesting a change.
- A review comment identifying a violation of a NON-NEGOTIABLE principle is blocking and MUST NOT
  be resolved by the author without a fix or a recorded amendment.

**Definition of Done**
A change is done when: the contract is updated, tests exist and pass, all gates are green, error
paths are handled and specified, observability is wired, security requirements are met, and the
documentation a consumer needs is published.

## Governance

This constitution supersedes all other development practices, conventions, and habits within this
project. Where a style guide, a tool default, or an existing file conflicts with it, this document
wins and the conflicting artifact MUST be corrected.

**Authority and scope**
- The constitution binds every specification, plan, task list, implementation, review, and future
  contribution, including automated and AI-assisted contributions.
- "MUST" and "MUST NOT" are absolute requirements. "SHOULD" indicates a strong default that may
  be departed from only with a recorded justification in the plan. NON-NEGOTIABLE principles admit
  no exception short of a formal amendment.

**Amendment procedure**
1. Propose the change as a pull request that edits this file and states the problem, the proposed
   text, and the migration impact on existing code.
2. Obtain approval from the project maintainers. Amendments to NON-NEGOTIABLE principles require
   unanimous maintainer approval.
3. On merge, update the version and the Last Amended date, and record the change in the Sync
   Impact Report comment at the top of this file.
4. If the amendment invalidates existing code, the PR MUST include either the corrections or a
   dated remediation plan tracked as work items.

**Versioning policy**
This document is versioned with semantic versioning:
- **MAJOR**: a principle is removed or redefined in a backward-incompatible way, or governance
  authority changes.
- **MINOR**: a principle or a materially new section is added, or existing guidance is expanded
  in a way that imposes new obligations.
- **PATCH**: clarification, wording, or typographical correction that does not change obligations.

**Compliance review**
- Constitutional compliance is verified at three gates: specification review, plan Constitution
  Check, and pull request review.
- Recorded deviations MUST be reviewed at least quarterly; a deviation that no longer has a
  removal plan MUST be either remediated or promoted to an amendment.
- Complexity MUST be justified. When two designs satisfy the requirements, the simpler one is the
  compliant one.
- Agent-specific operational guidance lives in `CLAUDE.md` at the repository root; that file MUST
  remain consistent with this constitution and MUST NOT relax any requirement stated here.

**Version**: 1.0.0 | **Ratified**: 2026-08-25 | **Last Amended**: 2026-08-25
