---
description: "Task list for Quick Note — Note Management API"
---

# Tasks: Quick Note — Note Management API

**Input**: Design documents from `/specs/001-note-management-api/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/openapi.yaml](./contracts/openapi.yaml)

**Tests**: Test tasks are **mandatory** in this feature. Constitution Principle II is NON-NEGOTIABLE
TDD, and the plan names TDD as the required workflow. Every implementation task in this list is
preceded by a test task that MUST be written and MUST fail first.

**Organization**: Tasks are grouped by user story so each story can be implemented, tested, and
demonstrated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Exact file paths are included in every task

## Path Conventions

Single Quarkus project at the repository root, per plan.md:

- Main: `src/main/java/dev/quicknote/`
- Resources: `src/main/resources/`
- Tests: `src/test/java/dev/quicknote/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, build gates, and local infrastructure

- [X] T001 Create Maven project at `pom.xml` with `quarkus-bom` 3.38.3, `maven.compiler.release=25`, and extensions `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-oidc`, `quarkus-flyway`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`; and declare explicit pinned versions for everything the BOM does **not** manage — Bucket4j, Caffeine, ArchUnit, and swagger-request-validator-restassured as dependencies, plus Jacoco, Spotless, OWASP Dependency-Check, and the Gatling Maven plugin as plugins. No floating or inherited-by-accident version, per the constitution's pinning rule
- [X] T002 [P] Create the package skeleton under `src/main/java/dev/quicknote/` — `notes/{api,application,domain,infrastructure}`, `security/`, `shared/`, `platform/` — each with a `package-info.java` stating the layer's dependency rule
- [X] T003 [P] Add `.gitignore` (target, .env, .quarkus) and `.env.example` documenting every configuration variable listed in quickstart.md
- [X] T004 [P] Configure Spotless with the Palantir Java format in `pom.xml`, bound to the `verify` phase so formatting violations fail the build
- [X] T005 [P] Configure the Jacoco plugin in `pom.xml` with a check rule of 80% line coverage overall and 100% for every path the constitution names — authentication (`dev.quicknote.security.*`), authorization (`dev.quicknote.notes.application.*`, where ownership is checked), and input validation (`dev.quicknote.notes.api.dto.*`, `dev.quicknote.shared.pagination.*`, `dev.quicknote.shared.problem.*`, and the classes `dev.quicknote.notes.domain.Note` and `dev.quicknote.notes.domain.Label`, which hold the invariants)
- [X] T006 [P] Configure the OWASP Dependency-Check plugin in `pom.xml` to fail the build on CVSS ≥ 7.0
- [X] T007 [P] Enforce the constitution's change-management rules: add `commitlint.config.js` with the Conventional Commits ruleset and a `commit-msg` hook wired through `.githooks/`, add a commit-message lint step to `.github/workflows/ci.yml` that checks every commit in a pull request, and record in `README.md` the branch-protection settings a maintainer must enable on the hosting platform — require a pull request, require one approving review, require all status checks, and block direct pushes to the default branch. The hook and CI step are in scope here; enabling branch protection is a one-time repository-administration action that cannot be done from the build, so it is documented rather than automated
- [X] T008 [P] Write `Dockerfile` using the `ubi9/openjdk-25-runtime` base with the Quarkus fast-jar layout, running as a non-root user
- [X] T009 [P] Write `docker-compose.yml` provisioning PostgreSQL 17 on host port 5432, Keycloak on host port 8180 importing `docker/keycloak/quicknote-realm.json`, a one-shot `flyway` migration service, and the app service on host port 8080 that depends on the migration service completing successfully — pin every host port explicitly so the commands in quickstart.md work verbatim
- [X] T010 [P] Create `docker/keycloak/quicknote-realm.json` seeding the `quicknote` realm, the `quicknote-api` client, and users `alice` and `bob` for isolation testing, and point both the Compose Keycloak service and the Dev Services realm import at that one file — it must live outside `src/test/resources/`, which is not on the container build path
- [X] T011 Configure `src/main/resources/application.properties` with `dev`/`test`/`prod` profiles: Dev Services for PostgreSQL and Keycloak in dev/test, `%dev.quarkus.keycloak.devservices.port=8180` so the dev-mode issuer URL matches the one documented in quickstart.md (pinned in `dev` only — leaving it random in `test` keeps parallel test runs from colliding), `quarkus.http.auth.permission.authenticated` denying all paths by default with `/q/health/*` explicitly permitted, and `quarkus.flyway.migrate-at-start=false` in `prod`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cross-cutting infrastructure every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T012 Write the ArchUnit layer-enforcement test in `src/test/java/dev/quicknote/architecture/LayerDependencyTest.java` asserting that `..notes.domain..` imports no `jakarta.persistence`, `jakarta.ws.rs`, `io.quarkus`, or `..infrastructure..` type, and that `..infrastructure.entity..` types are referenced only from `..infrastructure..`
- [X] T013 Write the credential-scope architecture test in `src/test/java/dev/quicknote/architecture/CredentialScopeTest.java` asserting that the service issues no credential and stores none: no Jakarta REST resource method exposes a token, login, logout, refresh, or credential-reset route; no JPA entity or Flyway migration declares a password, secret, or token column; and `quarkus.oidc.application-type` is `service`, never `web-app` (FR-005)
- [X] T014 Write the Flyway migration `src/main/resources/db/migration/V<YYYYMMDDHHmm>__notes.sql` (timestamp-versioned — see Notes) creating the `pg_trgm` extension, the `notes` table per data-model.md (including `state`, `previous_state`, `trashed_at`, `version`), the three `CHECK` constraints (`ck_notes_content`, `ck_notes_pinned_active`, `ck_notes_trashed_at`), and the `ix_notes_owner_listing` composite index
- [X] T015 [P] Write failing unit tests for the domain value types in `src/test/java/dev/quicknote/unit/domain/ValueTypeTest.java` covering `NoteColor` parsing of all eleven palette values, rejection of an unknown colour, and `NoteState` transitions
- [X] T016 [P] Implement the domain value types `NoteId`, `LabelId`, `UserId`, `NoteColor`, `NoteState` in `src/main/java/dev/quicknote/notes/domain/` as records and enums with no framework imports
- [X] T017 [P] Write failing tests for problem serialization in `src/test/java/dev/quicknote/unit/problem/ProblemDetailTest.java` asserting the RFC 9457 field set, the `code` field, and that no stack trace or SQL text can appear in `detail`
- [X] T018 Implement `ProblemDetail`, the sealed `DomainException` hierarchy, and the `ExceptionMapper` set in `src/main/java/dev/quicknote/shared/problem/`, emitting `application/problem+json` for every code in contracts/error-catalog.md
- [X] T019 [P] Write failing tests for identity extraction in `src/test/java/dev/quicknote/unit/security/CurrentUserTest.java` asserting the user is read from the `sub` claim and that a `userId` supplied in the body, query, or a header is ignored
- [X] T020 [P] Implement `CurrentUser` and `CurrentUserProducer` in `src/main/java/dev/quicknote/security/`, deriving identity solely from the injected `JsonWebToken`
- [X] T021 [P] Write failing tests for pagination binding in `src/test/java/dev/quicknote/unit/pagination/PageParamsTest.java` asserting the default size of 25, rejection of `size=101` with `400`, and rejection of a negative `page`
- [X] T022 [P] Implement `PageParams` and the domain `Page<T>` record in `src/main/java/dev/quicknote/shared/pagination/` and `src/main/java/dev/quicknote/notes/domain/Page.java`
- [X] T023 [P] Implement the correlation-ID filter in `src/main/java/dev/quicknote/shared/correlation/CorrelationFilter.java`, accepting `traceparent` or `X-Request-Id`, generating one when absent, placing it in the MDC, and echoing it on the response
- [X] T024 [P] Add `quarkus-logging-json` and configure structured JSON logging with the correlation ID in the MDC in `src/main/resources/application.properties`
- [X] T025 [P] Implement startup configuration validation with `@ConfigMapping` and `@NotBlank` constraints in `src/main/java/dev/quicknote/platform/config/QuickNoteConfig.java` so the process refuses to start on missing required values
- [X] T026 Implement the shared test harness in `src/test/java/dev/quicknote/support/ApiTestBase.java` providing a `@QuarkusTest` base with Dev Services, RestAssured, and token acquisition for `alice` and `bob`
- [X] T027 Wire `swagger-request-validator-restassured` into `src/test/java/dev/quicknote/support/ContractValidation.java` so every integration response is validated against `specs/001-note-management-api/contracts/openapi.yaml`
- [X] T028 Add the contract-drift CI check in `pom.xml` and `.github/workflows/ci.yml` comparing the `quarkus-smallrye-openapi` output to the authored `contracts/openapi.yaml`, failing on divergence

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Capture and revisit a note (Priority: P1) 🎯 MVP

**Goal**: An authenticated user can create a note, list their collection, retrieve one note, edit it,
and remove it from view — with strict per-user isolation.

**Independent Test**: Create a note, confirm it appears in the listing, retrieve it by id, update its
title and body, confirm the change persists on a fresh read, delete it, confirm it is gone from the
default listing, and confirm `bob` receives `404` for every one of `alice`'s notes.

**Scope note**: FR-013 groups title, body, and **colour** updates together. Colour is deliberately
deferred to US2 (Phase 4), where it belongs with the other organisational attributes. US1's
acceptance scenarios do not exercise colour, so the MVP is complete against its own tests; FR-013 is
fully satisfied only once Phase 4 lands.

### Tests for User Story 1 ⚠️

> **Write these tests FIRST and confirm they FAIL before any implementation task in this phase**

- [X] T029 [P] [US1] Write failing unit tests for `Note` invariants in `src/test/java/dev/quicknote/unit/domain/NoteTest.java` — title and body not both empty, whitespace-only treated as empty, defaults on creation, `updatedAt` advancing on change
- [X] T030 [P] [US1] Write failing contract tests for `POST /v1/notes` in `src/test/java/dev/quicknote/contract/CreateNoteContractTest.java` — `201` with `Location`, schema conformance, `400 note_content_required` on an empty body, `400 validation_failed` on an over-length title
- [X] T031 [P] [US1] Write failing contract tests for `GET /v1/notes` and `GET /v1/notes/{noteId}` in `src/test/java/dev/quicknote/contract/ReadNoteContractTest.java` — page envelope conformance, `404 note_not_found`, `400 malformed_identifier` on a non-UUID path value
- [X] T032 [P] [US1] Write failing contract tests for `PATCH /v1/notes/{noteId}` and `DELETE /v1/notes/{noteId}` in `src/test/java/dev/quicknote/contract/UpdateNoteContractTest.java` — partial update semantics, explicit `null` clearing a field, `204` on delete
- [X] T033 [P] [US1] Write failing malformed-request tests in `src/test/java/dev/quicknote/contract/MalformedRequestContractTest.java` asserting that a syntactically invalid JSON body, a body carrying an unknown property, and a body of the wrong JSON type each return `400 malformed_request` as a contract-conformant problem document, and that none of them reaches the service layer
- [X] T034 [P] [US1] Write the failing isolation test in `src/test/java/dev/quicknote/integration/NoteIsolationTest.java` asserting that `bob` receives `404` — never `403` — on read, update, and delete of `alice`'s note
- [X] T035 [P] [US1] Write failing authentication tests in `src/test/java/dev/quicknote/integration/AuthenticationTest.java` — `401` with no token, an expired token, a malformed token, and a token from a wrong audience, with a response body that does not reveal which check failed
- [X] T036 [P] [US1] Write the failing end-to-end journey test in `src/test/java/dev/quicknote/integration/CaptureAndReviseJourneyTest.java` covering create → list → read → update → delete
- [X] T037 [P] [US1] Write failing tests for idempotent creation in `src/test/java/dev/quicknote/integration/IdempotentCreateTest.java` — a replayed `Idempotency-Key` returns the original response and creates no second note; the same key from a different user creates a separate note
- [X] T038 [P] [US1] Write the failing text-fidelity test in `src/test/java/dev/quicknote/integration/TextFidelityTest.java` asserting that multi-line, emoji, and non-Latin content round-trips byte-for-byte
- [X] T039 [P] [US1] Write the failing concurrent-edit test in `src/test/java/dev/quicknote/integration/ConcurrentUpdateTest.java` asserting that two overlapping updates to one note leave the collection internally consistent, that the losing write returns `409 note_version_conflict`, and that `updatedAt` reflects the surviving state (spec edge case: Concurrent edits)

### Implementation for User Story 1

- [X] T040 [US1] Implement the `Note` domain aggregate in `src/main/java/dev/quicknote/notes/domain/Note.java` with the constructor and mutators enforcing every class invariant from data-model.md, using no framework types
- [X] T041 [P] [US1] Declare the `NoteRepository` port in `src/main/java/dev/quicknote/notes/domain/port/NoteRepository.java` in terms of domain types only
- [X] T042 [P] [US1] Implement `NoteEntity` in `src/main/java/dev/quicknote/notes/infrastructure/entity/NoteEntity.java` with `@Version` optimistic locking, package-private outside `infrastructure`
- [X] T043 [US1] Implement `NoteMapper` (entity ↔ domain) in `src/main/java/dev/quicknote/notes/infrastructure/mapper/NoteMapper.java`
- [X] T044 [US1] Implement `PanacheNoteRepository` in `src/main/java/dev/quicknote/notes/infrastructure/PanacheNoteRepository.java` using `PanacheRepositoryBase<NoteEntity, UUID>` with parameterized queries only, ordering by `updatedAt DESC, id DESC`
- [X] T045 [US1] Implement `NoteService` in `src/main/java/dev/quicknote/notes/application/NoteService.java` with `@Transactional` create, get, list, update, and trash operations, checking ownership on every access before any read or write
- [X] T046 [US1] Map `jakarta.persistence.OptimisticLockException` to `409 note_version_conflict` in `src/main/java/dev/quicknote/shared/problem/`, so the optimistic lock declared on `NoteEntity` surfaces as the contractual error code rather than a `500`
- [X] T047 [P] [US1] Implement the request and response DTOs in `src/main/java/dev/quicknote/notes/api/dto/` matching the `Note`, `CreateNoteRequest`, and `UpdateNoteRequest` schemas, with Jackson configured to reject unknown properties
- [X] T048 [US1] Implement `NoteResource` in `src/main/java/dev/quicknote/notes/api/NoteResource.java` exposing `POST /v1/notes`, `GET /v1/notes`, `GET /v1/notes/{noteId}`, `PATCH /v1/notes/{noteId}`, and `DELETE /v1/notes/{noteId}`, annotated `@RunOnVirtualThread`
- [X] T049 [US1] Write the Flyway migration `src/main/resources/db/migration/V<YYYYMMDDHHmm>__idempotency_keys.sql` creating the `idempotency_keys` table with composite primary key `(owner_id, key)`
- [X] T050 [US1] Implement the idempotency filter and store in `src/main/java/dev/quicknote/shared/idempotency/` so a replayed `Idempotency-Key` on note creation returns the original status and body
- [X] T051 [US1] Add structured log statements for note create, update, and delete in `NoteService`, carrying the note id, owner `sub`, and correlation id only — never title, body, or token content

**Checkpoint**: User Story 1 is fully functional and independently demonstrable. This is the MVP.

---

## Phase 4: User Story 2 - Organize with pin, color, and archive (Priority: P2)

**Goal**: A user can pin notes to the top, colour them, and archive the ones they no longer want in
the default view.

**Independent Test**: Create several notes, pin two, archive one, colour another; confirm pinned
notes sort first, the archived note is absent from the default listing and present under
`?state=archived`, and an out-of-palette colour is rejected.

### Tests for User Story 2 ⚠️

- [X] T052 [P] [US2] Write failing unit tests for state transitions in `src/test/java/dev/quicknote/unit/domain/NoteStateTransitionTest.java` — archiving clears `pinned`, trashing clears `pinned`, unarchiving restores to `ACTIVE`, and pinned can never coexist with archived or trashed
- [X] T053 [P] [US2] Write failing contract tests for state changes in `src/test/java/dev/quicknote/contract/NoteStateContractTest.java` — `PATCH` with `pinned`, `archived`, and `color`, plus `400 note_color_invalid` listing the permitted values
- [X] T054 [P] [US2] Write the failing ordering test in `src/test/java/dev/quicknote/integration/NoteOrderingTest.java` asserting pinned-first then most-recently-updated-first, and that unpinning returns the note to its ordinary position
- [X] T055 [P] [US2] Write the failing state-filter test in `src/test/java/dev/quicknote/integration/NoteStateFilterTest.java` asserting archived notes are excluded from the default listing and returned under `?state=archived`
- [X] T056 [P] [US2] Write the failing database-constraint test in `src/test/java/dev/quicknote/integration/NoteConstraintTest.java` asserting that a direct insert violating `ck_notes_pinned_active` is rejected by PostgreSQL

### Implementation for User Story 2

- [X] T057 [US2] Extend the `Note` aggregate in `src/main/java/dev/quicknote/notes/domain/Note.java` with `pin()`, `unpin()`, `archive()`, `unarchive()`, and `recolor()`, each re-asserting the invariants
- [X] T058 [US2] Extend `NoteService` in `src/main/java/dev/quicknote/notes/application/NoteService.java` to apply `pinned`, `archived`, and `color` changes from a partial update in a single transaction
- [X] T059 [US2] Extend `PanacheNoteRepository` in `src/main/java/dev/quicknote/notes/infrastructure/PanacheNoteRepository.java` with the pinned-first ordering and the `state` filter, backed by `ix_notes_owner_listing`
- [X] T060 [US2] Extend `UpdateNoteRequest` and the response mapping in `src/main/java/dev/quicknote/notes/api/dto/` to carry `pinned`, `archived`, and `color`, mapping the domain `NoteState` to the contract's `archived`/`trashed` booleans
- [X] T061 [US2] Add the `state` query parameter to `NoteResource` in `src/main/java/dev/quicknote/notes/api/NoteResource.java` with `active` as the default
- [X] T062 [US2] Verify with `EXPLAIN ANALYZE` in `src/test/java/dev/quicknote/integration/NoteQueryPlanTest.java` that the ordered listing query uses `ix_notes_owner_listing` and performs no sequential scan

**Checkpoint**: User Stories 1 and 2 both work independently

---

## Phase 5: User Story 3 - Organize with custom labels (Priority: P3)

**Goal**: A user can create labels, attach them to notes, filter by them, rename them, and delete
them without losing notes.

**Independent Test**: Create two labels, attach both to one note and one to another, filter by each
and confirm membership, rename one and confirm notes resolve under the new name, delete one and
confirm the notes survive with it detached.

### Tests for User Story 3 ⚠️

- [X] T063 [P] [US3] Write failing unit tests for `Label` in `src/test/java/dev/quicknote/unit/domain/LabelTest.java` — trimming, rejection of blank and over-length names, and case-insensitive name equality
- [X] T064 [P] [US3] Write failing unit tests for attachment rules in `src/test/java/dev/quicknote/unit/domain/NoteLabelTest.java` — the 20-label ceiling, duplicate attach as a no-op, detach of an unattached label as a no-op
- [X] T065 [P] [US3] Write failing contract tests for the label endpoints in `src/test/java/dev/quicknote/contract/LabelContractTest.java` — `POST`, `GET`, `PATCH`, `DELETE /v1/labels`, and `409 label_name_conflict`
- [X] T066 [P] [US3] Write failing contract tests for attachment in `src/test/java/dev/quicknote/contract/NoteLabelContractTest.java` — `PUT`/`DELETE /v1/notes/{noteId}/labels/{labelId}` returning `204` idempotently, `PUT /v1/notes/{noteId}/labels` replacing the set, and `422 note_label_limit_exceeded`
- [X] T067 [P] [US3] Write the failing uniqueness test in `src/test/java/dev/quicknote/integration/LabelUniquenessTest.java` asserting that `work`, `WORK`, and `  Work  ` collide for one user but not across users, including a concurrent-create race
- [X] T068 [P] [US3] Write the failing label-lifecycle test in `src/test/java/dev/quicknote/integration/LabelLifecycleTest.java` asserting that renaming leaves note content untouched and deleting detaches from every note while deleting no note
- [X] T069 [P] [US3] Write the failing cross-user label test in `src/test/java/dev/quicknote/integration/LabelIsolationTest.java` asserting that attaching `bob`'s label to `alice`'s note returns `404 label_not_found`

### Implementation for User Story 3

- [X] T070 [US3] Write the Flyway migration `src/main/resources/db/migration/V<YYYYMMDDHHmm>__labels.sql` creating `labels`, `note_labels` with composite primary key and `ON DELETE CASCADE` on both sides, the `ux_labels_owner_name` unique index on `(owner_id, lower(trim(name)))`, and `ix_note_labels_label`
- [X] T071 [P] [US3] Implement the `Label` domain entity in `src/main/java/dev/quicknote/notes/domain/Label.java` with trimming and length invariants
- [X] T072 [P] [US3] Declare the `LabelRepository` port in `src/main/java/dev/quicknote/notes/domain/port/LabelRepository.java`
- [X] T073 [P] [US3] Implement `LabelEntity` and `NoteLabelEntity` in `src/main/java/dev/quicknote/notes/infrastructure/entity/`
- [X] T074 [US3] Implement `PanacheLabelRepository` and `LabelMapper` in `src/main/java/dev/quicknote/notes/infrastructure/`, translating the unique-constraint violation into the `label_name_conflict` domain exception
- [X] T075 [US3] Extend the `Note` aggregate in `src/main/java/dev/quicknote/notes/domain/Note.java` with `attachLabel()`, `detachLabel()`, and `replaceLabels()`, enforcing the 20-label ceiling and no-op semantics
- [X] T076 [US3] Implement `LabelService` in `src/main/java/dev/quicknote/notes/application/LabelService.java` with create, list, rename, and delete, checking ownership on every operation
- [X] T077 [US3] Extend `NoteService` in `src/main/java/dev/quicknote/notes/application/NoteService.java` with attach, detach, and replace-set operations that verify the label is owned by the caller before attaching
- [X] T078 [P] [US3] Implement the label DTOs in `src/main/java/dev/quicknote/notes/api/dto/` matching the `Label` and `LabelRequest` schemas
- [X] T079 [US3] Implement `LabelResource` in `src/main/java/dev/quicknote/notes/api/LabelResource.java` exposing `GET`/`POST /v1/labels` and `PATCH`/`DELETE /v1/labels/{labelId}`
- [X] T080 [US3] Add the attachment endpoints to `NoteResource` in `src/main/java/dev/quicknote/notes/api/NoteResource.java` — `PUT /v1/notes/{noteId}/labels`, `PUT`/`DELETE /v1/notes/{noteId}/labels/{labelId}`
- [X] T081 [US3] Add the `labelId` filter to the listing query in `src/main/java/dev/quicknote/notes/infrastructure/PanacheNoteRepository.java` and expose it on `NoteResource`

**Checkpoint**: User Stories 1, 2, and 3 all work independently

---

## Phase 6: User Story 4 - Find a note quickly (Priority: P4)

**Goal**: A user can find a note by a remembered phrase, and combine that search with state and
label filters and pagination.

**Independent Test**: Create notes with distinct words in titles and bodies, search for a term
present only in one body and confirm exactly that note returns; combine the term with a label filter
and confirm the intersection; confirm a case-mismatched term still matches.

### Tests for User Story 4 ⚠️

- [X] T082 [P] [US4] Write failing contract tests for search in `src/test/java/dev/quicknote/contract/SearchContractTest.java` — `?q=` conformance, `400` on `size=101`, and `200` with an empty `items` array when nothing matches
- [X] T083 [P] [US4] Write the failing search-semantics test in `src/test/java/dev/quicknote/integration/NoteSearchTest.java` — matches in title and in body, case-insensitivity, substring (not whole-word) matching, and exclusion of trashed notes unless `state=trashed`
- [X] T084 [P] [US4] Write the failing search-escaping test in `src/test/java/dev/quicknote/integration/SearchEscapingTest.java` asserting that a term containing `%`, `_`, or `\` is matched literally and never alters the query
- [X] T085 [P] [US4] Write the failing combined-filter test in `src/test/java/dev/quicknote/integration/CombinedFilterTest.java` asserting that `q`, `state`, `labelId`, and pagination apply together in one request
- [X] T086 [P] [US4] Write the failing pagination-consistency test in `src/test/java/dev/quicknote/integration/PaginationTest.java` asserting correct `totalElements`, `totalPages`, `nextPage` being null on the last page, and no item appearing on two pages of a static collection

### Implementation for User Story 4

- [X] T087 [US4] Write the Flyway migration `src/main/resources/db/migration/V<YYYYMMDDHHmm>__search_indexes.sql` creating the GIN trigram indexes `ix_notes_title_trgm` and `ix_notes_body_trgm`
- [X] T088 [US4] Implement the search predicate in `src/main/java/dev/quicknote/notes/infrastructure/PanacheNoteRepository.java` using parameter-bound `ILIKE` across title and body, with `%`, `_`, and `\` escaped in the term before binding
- [X] T089 [US4] Extend `NoteService` in `src/main/java/dev/quicknote/notes/application/NoteService.java` to accept an optional search term alongside the state and label filters
- [X] T090 [US4] Add the `q` query parameter to `NoteResource` in `src/main/java/dev/quicknote/notes/api/NoteResource.java` with the contract's length bounds
- [X] T091 [US4] Verify with `EXPLAIN ANALYZE` in `src/test/java/dev/quicknote/integration/SearchQueryPlanTest.java` that the search query uses the trigram indexes and performs no sequential scan on a seeded dataset

**Checkpoint**: User Stories 1–4 all work independently

---

## Phase 7: User Story 5 - Recover from mistakes with the trash (Priority: P5)

**Goal**: Deleted notes are recoverable for 30 days, can be permanently removed on demand, and are
purged automatically thereafter.

**Independent Test**: Delete a note, confirm it is absent from the default view and present in
`/v1/trash`, restore it and confirm it returns intact to its prior state, then permanently delete
another and confirm it is unrecoverable.

### Tests for User Story 5 ⚠️

- [X] T092 [P] [US5] Write failing unit tests for trash transitions in `src/test/java/dev/quicknote/unit/domain/NoteTrashTest.java` — restore returns to `ACTIVE` or `ARCHIVED` per `previous_state`, repeated trashing leaves `trashedAt` unchanged, and edit/pin/archive/label on a trashed note raises `note_trashed`
- [X] T093 [P] [US5] Write failing contract tests for the trash endpoints in `src/test/java/dev/quicknote/contract/TrashContractTest.java` — `GET`/`DELETE /v1/trash` and `DELETE /v1/trash/{noteId}`, plus `404` for a note that is not in the trash
- [X] T094 [P] [US5] Write the failing restore-fidelity test in `src/test/java/dev/quicknote/integration/TrashRestoreTest.java` asserting that title, body, colour, and labels survive a trash-and-restore cycle, and that an archived note restores to archived
- [X] T095 [P] [US5] Write the failing permanent-deletion test in `src/test/java/dev/quicknote/integration/TrashPurgeTest.java` asserting that purge and empty-trash are irreversible, affect no active or archived note, and delete no labels
- [X] T096 [P] [US5] Write the failing retention test in `src/test/java/dev/quicknote/integration/RetentionJobTest.java` asserting that a note trashed more than 30 days ago is removed by the job and one trashed 29 days ago is not
- [X] T097 [P] [US5] Write the failing label-deleted-during-trash test in `src/test/java/dev/quicknote/integration/RestoreAfterLabelDeletionTest.java` asserting the note restores successfully without the deleted label

### Implementation for User Story 5

- [X] T098 [US5] Write the Flyway migration `src/main/resources/db/migration/V<YYYYMMDDHHmm>__trash_and_scheduler.sql` creating the partial index `ix_notes_trashed_at` on trashed rows and the Quartz clustered job-store tables
- [X] T099 [US5] Extend the `Note` aggregate in `src/main/java/dev/quicknote/notes/domain/Note.java` with `trash()` recording `previous_state` and `restore()` returning to it
- [X] T100 [US5] Implement `TrashService` in `src/main/java/dev/quicknote/notes/application/TrashService.java` with list, restore, purge-one, and empty operations, all ownership-checked
- [X] T101 [US5] Extend `PanacheNoteRepository` in `src/main/java/dev/quicknote/notes/infrastructure/PanacheNoteRepository.java` with the trashed listing ordered by `trashedAt DESC`, bulk purge, and the retention sweep by cutoff
- [X] T102 [US5] Implement `TrashResource` in `src/main/java/dev/quicknote/notes/api/TrashResource.java` exposing `GET /v1/trash`, `DELETE /v1/trash`, and `DELETE /v1/trash/{noteId}`
- [X] T103 [US5] Wire restore into `PATCH /v1/notes/{noteId}` in `src/main/java/dev/quicknote/notes/api/NoteResource.java` so `{"trashed": false}` restores and any other change to a trashed note returns `409 note_trashed`
- [X] T104 [US5] Implement the clustered retention job in `src/main/java/dev/quicknote/platform/scheduler/RetentionJob.java` using `quarkus-quartz` with a JDBC job store, deleting in bounded batches and also expiring idempotency keys older than 24 hours
- [X] T105 [US5] Add `quarkus-quartz` clustered configuration in `src/main/resources/application.properties` with the retention window read from `QUICKNOTE_TRASH_RETENTION_DAYS`, defaulting to 30

**Checkpoint**: All five user stories are independently functional

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Constitutional requirements that span every story, plus final validation

- [X] T106 [P] Write the failing full-surface authorization test in `src/test/java/dev/quicknote/integration/FullSurfaceIsolationTest.java`, parameterized over **all fifteen operations** in `contracts/openapi.yaml`: for every operation addressing a specific note or label, assert `bob` receives `404` — never `403`, never a body carrying `alice`'s content; for every collection operation (`GET /v1/notes`, `POST /v1/notes`, `GET /v1/labels`, `POST /v1/labels`, `GET /v1/trash`, `DELETE /v1/trash`), assert it returns or affects only the caller's own data (SC-004, SC-009)
- [X] T107 Close any authorization gap the full-surface test exposes in `src/main/java/dev/quicknote/notes/application/`, so every one of the fifteen operations verifies ownership before any read or write
- [X] T108 [P] Write failing tests for rate limiting in `src/test/java/dev/quicknote/integration/RateLimitTest.java` asserting `429` with `Retry-After` when the per-principal limit is exceeded, and independently when the per-IP limit is exceeded
- [X] T109 Implement the Bucket4j rate-limit filter in `src/main/java/dev/quicknote/platform/ratelimit/RateLimitFilter.java` with a Caffeine-backed store, one bucket per principal and one per source IP, behind an interface whose backing store can be swapped without touching the filter
- [X] T110 [P] Write the failing edge-limits test in `src/test/java/dev/quicknote/integration/PayloadLimitTest.java` covering the byte-limit/character-limit boundary, which is where this is easy to get wrong: a body of 20,000 four-byte characters (~80 KB of UTF-8, e.g. emoji or CJK) is **accepted** with `201`, because 20,000 characters is the limit and bytes are not characters (FR-014, FR-042); a body of 20,001 Latin characters returns `400 validation_failed` naming the `body` field with code `too_long` — **never** `413`, since a `413` carries no field and would defeat FR-014 and SC-007; a body above the configured byte ceiling returns `413 payload_too_large` before any handler runs and writes no partial state; an oversized header set is rejected; and a request exceeding the configured read timeout is terminated rather than held open
- [X] T111 Configure explicit payload size limits and request timeouts in `src/main/resources/application.properties` — `quarkus.http.limits.max-body-size=256K`, `quarkus.http.limits.max-header-size`, `quarkus.http.read-timeout`, and `quarkus.http.idle-timeout` — and map the oversize rejection to `413 payload_too_large` in `src/main/java/dev/quicknote/shared/problem/`. The byte ceiling is a coarse abuse guard, deliberately set far above any legitimate note: a maximal note is 20,000 characters plus a 200-character title plus 20 label identifiers, which is at most ~82 KB of UTF-8 even when every character is four bytes, and more once JSON-escaped. 256K leaves room for that while still refusing a payload no client should send. Sizing this ceiling near the character limit is the failure this guards against — it would `413` valid non-Latin notes and would replace FR-014's field-naming `400` with a fieldless `413`
- [X] T112 [P] Write failing readiness tests in `src/test/java/dev/quicknote/integration/HealthTest.java` asserting that `/q/health/ready` reports `DOWN` when the datasource is unreachable and that `/q/health/live` and `/q/health/ready` are the only unauthenticated paths
- [X] T113 [P] Implement the readiness check in `src/main/java/dev/quicknote/platform/health/ReadinessCheck.java` verifying the datasource and OIDC issuer reachability
- [X] T114 [P] Add `quarkus-micrometer-registry-prometheus` and `quarkus-opentelemetry` configuration in `src/main/resources/application.properties`, and assert in `src/test/java/dev/quicknote/integration/MetricsCardinalityTest.java` that HTTP metrics are labelled by URI template rather than concrete path
- [X] T115 [P] Write the log-content test in `src/test/java/dev/quicknote/unit/LogContentTest.java` asserting that no log statement includes a note title, body, label name, or token value
- [X] T116 [P] Add the latency test in `src/test/java/dev/quicknote/perf/ListingPerformanceTest.java` under a `perf` Maven profile, seeding 1,000 notes for one user and asserting p95 under 1 s for the first page (SC-002), under 300 ms for a single-note read, and under 5 s end-to-end for creating a note and receiving its confirmation (SC-001)
- [X] T117 Add the concurrency load test as a **Gatling** simulation in `src/test/java/dev/quicknote/perf/ConcurrentLoadSimulation.java` (Java DSL, run by the `gatling-maven-plugin` under the `perf` profile), driving 500 concurrent users through a mixed capture, listing, and search workload for a sustained run and asserting that p95 stays within the SC-002 targets and that no request fails (SC-006)
- [X] T118 Write the no-write-on-reject test in `src/test/java/dev/quicknote/integration/RejectionIsInertTest.java`, parameterized over every operation that can reject a submission — note create and update, label create and rename, label attach and replace — asserting that after each `400`, `409`, `413`, and `422` the stored row count and the content of every pre-existing note and label are byte-for-byte unchanged (SC-007)
- [X] T119 [P] Write the limit-agreement test in `src/test/java/dev/quicknote/architecture/LimitAgreementTest.java` asserting that the title, body, label-name, label-count, and page-size limits are identical across all five places they appear — the Bean Validation annotations, the domain invariants, the SQL `CHECK` constraints and column widths, `contracts/openapi.yaml`'s `maxLength`/`maximum`, and the error messages — with spec.md §Assumptions as the normative source (D1)
- [X] T120 [P] Assert in `src/test/java/dev/quicknote/contract/ErrorContractTest.java` that every status code the service can emit is declared in `contracts/openapi.yaml` — including `413`, `500`, and `503` — so a fault surfaces as a contractual problem document rather than an undeclared response
- [X] T121 Complete `.github/workflows/ci.yml` wiring all six constitutional gates — format and lint, static analysis, tests with coverage thresholds, OpenAPI validation and contract conformance, dependency vulnerability scan, and migrations applying cleanly to a fresh database
- [X] T122 [P] Write `README.md` documenting the credential contract the service verifies — expected issuer, audience, signing method, and the `sub` claim — as required by FR-006
- [X] T123 [P] Publish the OpenAPI document at `/q/openapi` and Swagger UI in non-production profiles, configured in `src/main/resources/application.properties`
- [X] T124 Run every scenario in [quickstart.md](./quickstart.md) against the Compose stack and record any divergence between the running service and the contract
- [X] T125 Review the two deviations recorded in plan.md Complexity Tracking — instance-local rate limiting and dev/test `migrate-at-start` — confirming each still has a valid removal plan

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **blocks all user stories**
- **User Stories (Phases 3–7)**: All depend on Foundational completion
- **Polish (Phase 8)**: Depends on the user stories being delivered. The full-surface authorization test (T106), the concurrency load test (T117), and the no-write-on-reject test (T118) can only run once all fifteen operations exist, which is why they sit here rather than inside a story phase

### User Story Dependencies

- **US1 (P1)**: Depends only on Foundational. No dependency on any other story
- **US2 (P2)**: Depends on Foundational. Extends the `Note` aggregate and `NoteService` created in US1, so it is sequenced after US1 rather than run beside it
- **US3 (P3)**: Depends on Foundational. Adds its own aggregate and tables; touches `Note` for the label set. Can be built beside US2 by a second developer, since they modify different methods of the same aggregate — coordinate on `Note.java` and `NoteResource.java`
- **US4 (P4)**: Depends on Foundational and, for the label-filter combination test, on US3. The search implementation itself only needs US1
- **US5 (P5)**: Depends on Foundational and US1. Independent of US2, US3, and US4

### Within Each User Story

- Tests are written and confirmed failing before any implementation task in that phase
- Domain before ports; ports before adapters; adapters before services; services before resources
- Migrations before the repository code that queries the new columns or indexes

### Parallel Opportunities

- Setup: T002–T010 are all `[P]`; T001 and T011 are sequential anchors
- Foundational: T015–T017 and T019–T025 are `[P]` across distinct files; T012, T013, T014, T018, T026, T027, T028 are sequential anchors
- Every test task within a story phase is `[P]` — they live in separate files and share only the harness
- US2 and US3 can be developed by different people after US1, with a coordination point on `Note.java` and `NoteResource.java`
- Polish: T106, T108, T110, T112–T116, T119, T120, T122, T123 are `[P]`; T107, T109, T111, T117, T118, T121, T124, T125 depend on the test or config task immediately above them

---

## Parallel Example: User Story 1

```bash
# Write all US1 tests together and confirm every one fails:
Task: "Unit tests for Note invariants in src/test/java/dev/quicknote/unit/domain/NoteTest.java"
Task: "Contract tests for POST /v1/notes in src/test/java/dev/quicknote/contract/CreateNoteContractTest.java"
Task: "Contract tests for GET endpoints in src/test/java/dev/quicknote/contract/ReadNoteContractTest.java"
Task: "Contract tests for PATCH/DELETE in src/test/java/dev/quicknote/contract/UpdateNoteContractTest.java"
Task: "Malformed request tests in src/test/java/dev/quicknote/contract/MalformedRequestContractTest.java"
Task: "Isolation test in src/test/java/dev/quicknote/integration/NoteIsolationTest.java"
Task: "Authentication tests in src/test/java/dev/quicknote/integration/AuthenticationTest.java"
Task: "Journey test in src/test/java/dev/quicknote/integration/CaptureAndReviseJourneyTest.java"
Task: "Idempotency tests in src/test/java/dev/quicknote/integration/IdempotentCreateTest.java"
Task: "Text fidelity test in src/test/java/dev/quicknote/integration/TextFidelityTest.java"
Task: "Concurrent edit test in src/test/java/dev/quicknote/integration/ConcurrentUpdateTest.java"

# Then the parallelizable US1 implementation slices:
Task: "NoteRepository port in src/main/java/dev/quicknote/notes/domain/port/NoteRepository.java"
Task: "NoteEntity in src/main/java/dev/quicknote/notes/infrastructure/entity/NoteEntity.java"
Task: "DTOs in src/main/java/dev/quicknote/notes/api/dto/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup — T001–T011
2. Complete Phase 2: Foundational — T012–T028 (**blocks everything**)
3. Complete Phase 3: User Story 1 — T029–T051
4. **STOP and VALIDATE**: run quickstart.md scenarios 1 and 6; confirm capture, retrieval, edit,
   deletion, and per-user isolation
5. Demo or deploy — the service is a usable authenticated notepad at this point

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate → **MVP**
3. US2 → validate → organized collection
4. US3 → validate → labelled collection
5. US4 → validate → findable collection
6. US5 → validate → recoverable collection
7. Polish → constitutional gates green, quickstart clean

Each increment leaves the service deployable and breaks nothing before it.

### Parallel Team Strategy

1. Everyone completes Setup + Foundational together — it is the blocking phase
2. After the checkpoint:
   - Developer A: US1, then US5 (both centre on the note lifecycle)
   - Developer B: US3 (its own aggregate and tables), then US4
   - Developer C: US2, then Polish
3. Coordination points: `Note.java` and `NoteResource.java` are touched by four of the five stories.
   Sequence edits to those two files, or pair on them, rather than merging concurrently
4. Migrations need no coordination: because versions are timestamps rather than a sequence, two
   people authoring migrations on the same day cannot collide, and Flyway applies them in the order
   they were written regardless of the order the stories merge

---

## Notes

- **Migration naming**: every Flyway file is `V<YYYYMMDDHHmm>__<subject>.sql`, using the timestamp at
  which the migration is authored — for example `V202608251430__labels.sql`. Sequential `V1`/`V2`
  numbering was rejected because this plan has stories being built concurrently, and two branches
  each claiming `V3` collide at merge in a way Flyway reports only after the fact
- `[P]` tasks touch different files and have no dependency on incomplete work
- `[Story]` labels map each task to a user story for traceability
- **Every test task must be confirmed failing before its implementation task begins** — this is
  Constitution Principle II and it is not optional
- Commit after each task or logical group; each PR is scoped to one coherent change
- Stop at any checkpoint to validate a story independently
- Deferred by the spec and out of scope here: sharing, checklists, reminders, attachments, version
  history, manual ordering, bulk operations beyond emptying the trash, and export/import

---

## Phase 9: Convergence

**Purpose**: Close the gaps between the specified intent and the implemented code, found by
assessing the codebase against spec.md, plan.md, tasks.md, and the constitution on 2026-08-26.

- [ ] T126 **CRITICAL** Verify the credential's issuer in `src/main/resources/application.properties`: `quarkus.oidc.token.issuer=any` explicitly disables issuer verification, while FR-002 and Constitution IV require it and README.md publishes it as a check the service performs. Either bind the issuer to `QUARKUS_OIDC_AUTH_SERVER_URL` (leaving `any` only where Dev Services assigns a random port, and only in `dev`/`test`) or correct the published credential contract so it stops claiming a check that does not happen, per FR-002 (contradicts)
- [ ] T127 **CRITICAL** Verify the credential's intended audience in `src/main/resources/application.properties`: `quarkus.oidc.token.audience` is unset, so `aud` is never checked — `quarkus.oidc.client-id` alone does not validate it. Set the audience and add a test asserting a token minted for a different audience is refused, adjusting the realm in `docker/keycloak/quicknote-realm.json` if Keycloak must be told to emit `aud`; or correct README.md, per FR-002 (missing)
- [ ] T128 Preserve the active filters in the `nextPage` link built in `src/main/java/dev/quicknote/notes/api/dto/PageResponse.java` and its caller `src/main/java/dev/quicknote/notes/api/NoteResource.java`: the base path is passed bare, so a client that follows the link loses `q`, `state`, and `labelId` and silently receives unfiltered results. Add a test in `src/test/java/dev/quicknote/integration/PaginationTest.java` that actually follows `nextPage` and asserts the filtered set is preserved — no current test does, which is why this survived, per FR-040 (partial)
- [ ] T129 Fix the contract-drift gate in `scripts/contract-drift.py`: it compares the authored contract's paths (relative to the `/v1` server URL) against the generated document's absolute paths, so its first execution reported all fifteen operations as both undelivered and undeclared. Normalize the server prefix before comparing, then run it to confirm the two documents agree, per Constitution I / plan Gate 4b (contradicts)
- [ ] T130 Execute the Gatling simulation in `src/test/java/dev/quicknote/perf/ConcurrentLoadSimulation.java` against a running instance and record the outcome: it compiles and is wired to the `perf` profile but has never run, so the 500-concurrent-user capacity claim is unverified, per SC-006 (partial)
- [ ] T131 Reconcile the deny-by-default mechanism between plan.md and the code: the plan's Constitution Check states `quarkus.http.auth.permission.authenticated` denies all paths, while the implementation uses `@Authenticated` on every resource with `ResourceSecurityTest` failing the build if one is missing — the substitution was made because eager path permissions answer 401 below the JAX-RS layer with an empty body, breaking the RFC 9457 rule. Update plan.md to describe what was built and why, per plan: Constitution Check row IV (contradicts)
- [ ] T132 Run `mvn -Psecurity dependency-check:check` once and confirm the gate functions and the dependency tree is clean of high and critical advisories: it is configured but has never executed, and T129 shows that an unexercised gate can be silently non-functional, per Constitution IV (partial)
- [ ] T133 Document `QUICKNOTE_TRASH_PURGE_CRON` in `.env.example`: it is a `@NotBlank` value on `QuickNoteConfig.Trash` that the service refuses to start without, and it is the one configuration key the template omits, per plan/T003 (partial)
- [ ] T134 Replace the deprecated `quarkus.smallrye-openapi.enable` keys in `src/main/resources/application.properties` with the current property: every production boot logs a deprecation warning, which is noise in exactly the logs an operator reads during an incident, per Constitution V (partial)

**Checkpoint**: T126 and T127 close a gap between what the service claims to verify and what it
actually verifies; they are the two worth doing before anything else ships.
