# Phase 1 Data Model: Quick Note — Note Management API

**Date**: 2026-08-25 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Two representations exist for every concept, and the constitution requires them to stay separate:
the **domain model** (plain Java in `notes/domain/`, no JPA) and the **persistence model** (JPA
entities in `notes/infrastructure/entity/`, never visible outside that package). This document
defines both, plus the validation rules and state transitions each must enforce.

---

## Domain model

### Note (aggregate root)

| Field | Type | Rules |
|---|---|---|
| `id` | `NoteId` (UUIDv7 wrapper) | Assigned on creation, immutable (FR-011) |
| `ownerId` | `UserId` (Keycloak `sub`) | Immutable; derived only from the verified credential (FR-004) |
| `title` | `String?` | Optional; ≤ 200 chars after trim; whitespace-only becomes null (FR-014, FR-015) |
| `body` | `String?` | Optional; ≤ 20,000 chars after trim; whitespace-only becomes null |
| `color` | `NoteColor` | Never null; defaults to `DEFAULT` (FR-018, FR-019) |
| `pinned` | `boolean` | Defaults false; cannot be true while archived or trashed (FR-020) |
| `state` | `NoteState` | `ACTIVE` \| `ARCHIVED` \| `TRASHED`; defaults `ACTIVE` |
| `trashedAt` | `Instant?` | Non-null if and only if `state == TRASHED` (FR-022) |
| `labels` | `Set<LabelId>` | ≤ 20 entries; every entry owned by `ownerId` (FR-030, FR-032) |
| `createdAt` | `Instant` | Set once, immutable |
| `updatedAt` | `Instant` | Advances on every content or state change (FR-011) |
| `version` | `long` | Optimistic-lock counter |

**Class invariants** — enforced in the domain constructor and every mutator, so they hold without a
database and are unit-testable:

1. `title` and `body` are not both null. Violating this raises the `note_content_required` error
   (FR-010).
2. `pinned == true` implies `state == ACTIVE`.
3. `trashedAt != null` if and only if `state == TRASHED`.
4. `labels.size() <= 20`.
5. `state == TRASHED` forbids every mutation except `restore()` (FR-021).

**Why archived/trashed are a `state` enum rather than the spec's two booleans**: the spec describes
`archived` and `trashed` as independent statuses, but its own rules make three of the four boolean
combinations either impossible or meaningless — a trashed note is not meaningfully "archived", and
FR-020 forbids pinned+archived and pinned+trashed. Modelling one three-valued state makes the
illegal combinations unrepresentable instead of merely untested. The **wire contract keeps the
`archived` and `trashed` booleans** the spec describes; the mapping is total and lossless, and is
defined in [contracts/openapi.yaml](./contracts/openapi.yaml).

### Note state transitions

| From | Operation | To | Side effects | Rule |
|---|---|---|---|---|
| — | create | `ACTIVE` | `createdAt`/`updatedAt` set, `pinned=false`, `color=DEFAULT` | FR-019 |
| `ACTIVE` | archive | `ARCHIVED` | `pinned` cleared | FR-017, FR-020 |
| `ARCHIVED` | unarchive | `ACTIVE` | none | FR-017 |
| `ACTIVE` / `ARCHIVED` | trash | `TRASHED` | `pinned` cleared, `trashedAt` set, prior state recorded | FR-020, FR-022 |
| `TRASHED` | restore | prior state | `trashedAt` cleared | FR-023 |
| `TRASHED` | purge | *(row deleted)* | attachments cascade-deleted | FR-025 |
| `TRASHED` | trash (repeat) | `TRASHED` | no-op; `trashedAt` unchanged | Edge case: repeated deletion |
| `TRASHED` | edit / pin / archive / label | *rejected* | `409 note_trashed` | FR-021 |

`restore()` returns the note to whichever of `ACTIVE` or `ARCHIVED` it held before trashing, so a
`previous_state` column is required — restoring an archived note must not resurrect it into the
default view.

### Label

| Field | Type | Rules |
|---|---|---|
| `id` | `LabelId` (UUIDv7 wrapper) | Assigned on creation, immutable |
| `ownerId` | `UserId` | Immutable |
| `name` | `String` | Trimmed; non-empty; ≤ 50 chars; unique per owner case-insensitively (FR-027, FR-028) |
| `createdAt` | `Instant` | Set once |

Original casing is preserved for display; uniqueness compares `lower(trim(name))`.

### Note–Label attachment

Many-to-many between `Note` and `Label`, both owned by the same user. Attaching an already-attached
label and detaching an unattached label both succeed without changing anything (FR-029). Deleting a
label removes every attachment and deletes no notes (FR-031). Purging a note removes its attachments
and deletes no labels.

### NoteColor

Closed enum, eleven values, wire form is the lowercase name:
`DEFAULT`, `RED`, `ORANGE`, `YELLOW`, `GREEN`, `TEAL`, `BLUE`, `DARK_BLUE`, `PURPLE`, `PINK`,
`BROWN`. Any other value is rejected `400 note_color_invalid` with the permitted values listed
(FR-018).

### Page&lt;T&gt;

Transport-agnostic pagination result: `items`, `page`, `size`, `totalElements`, `totalPages`. Lives
in the domain so the application layer can return it without importing a transport type.

---

## Persistence model (PostgreSQL)

### `notes`

| Column | Type | Constraints |
|---|---|---|
| `id` | `uuid` | PK |
| `owner_id` | `varchar(255)` | NOT NULL |
| `title` | `varchar(200)` | NULL |
| `body` | `text` | NULL, `length(body) <= 20000` |
| `color` | `varchar(16)` | NOT NULL, DEFAULT `'default'` |
| `pinned` | `boolean` | NOT NULL, DEFAULT false |
| `state` | `varchar(16)` | NOT NULL, DEFAULT `'ACTIVE'` |
| `previous_state` | `varchar(16)` | NULL — the state to restore to |
| `trashed_at` | `timestamptz` | NULL |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |
| `version` | `bigint` | NOT NULL, DEFAULT 0 |

Table-level `CHECK` constraints mirror the domain invariants, so the database refuses an illegal row
even if a future code path forgets to ask:

- `ck_notes_content`: `title IS NOT NULL OR body IS NOT NULL`
- `ck_notes_pinned_active`: `NOT pinned OR state = 'ACTIVE'`
- `ck_notes_trashed_at`: `(state = 'TRASHED') = (trashed_at IS NOT NULL)`

### `labels`

| Column | Type | Constraints |
|---|---|---|
| `id` | `uuid` | PK |
| `owner_id` | `varchar(255)` | NOT NULL |
| `name` | `varchar(50)` | NOT NULL, `length(trim(name)) > 0` |
| `created_at` | `timestamptz` | NOT NULL |

### `note_labels`

| Column | Type | Constraints |
|---|---|---|
| `note_id` | `uuid` | FK → `notes(id)` ON DELETE CASCADE |
| `label_id` | `uuid` | FK → `labels(id)` ON DELETE CASCADE |

Composite PK `(note_id, label_id)` — this is what makes a duplicate attach a no-op rather than a
duplicate row. `ON DELETE CASCADE` on both sides implements FR-031 and the note-purge rule at the
storage level.

### `idempotency_keys`

| Column | Type | Constraints |
|---|---|---|
| `key` | `varchar(255)` | Part of PK |
| `owner_id` | `varchar(255)` | Part of PK |
| `note_id` | `uuid` | NOT NULL |
| `response_status` | `smallint` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |

Composite PK `(owner_id, key)` scopes keys per user so one user cannot probe or collide with
another's. Rows older than 24 hours are removed by the same scheduled job that purges the trash.

### Indexes

Every query the service issues has a matching index; the constitution treats an unindexed access
path as a defect.

| Index | Definition | Serves |
|---|---|---|
| `ix_notes_owner_listing` | `(owner_id, state, pinned DESC, updated_at DESC, id DESC)` | Default and filtered listings with pinned-first ordering (FR-033, FR-035) |
| `ix_notes_title_trgm` | GIN `(title gin_trgm_ops)` | `ILIKE '%term%'` on title (FR-036) |
| `ix_notes_body_trgm` | GIN `(body gin_trgm_ops)` | `ILIKE '%term%'` on body (FR-036) |
| `ix_notes_trashed_at` | `(trashed_at)` WHERE `state = 'TRASHED'` | Retention sweep (FR-024) |
| `ux_labels_owner_name` | UNIQUE `(owner_id, lower(trim(name)))` | Case-insensitive per-user uniqueness (FR-027) |
| `ix_note_labels_label` | `(label_id, note_id)` | Filter notes by label; cascade on label delete (FR-034) |

`CREATE EXTENSION IF NOT EXISTS pg_trgm;` is the first statement of the initial notes migration.
Migrations are named `V<YYYYMMDDHHmm>__<subject>.sql` — timestamp-versioned rather than sequentially
numbered, so two people building different stories in parallel cannot collide on a version number.

---

## Validation rules by origin

| Rule | Enforced at | Failure |
|---|---|---|
| Title ≤ 200, body ≤ 20,000, label name ≤ 50 | Bean Validation at the resource boundary, again as a domain invariant, again as a DB constraint | `400 validation_failed` with per-field `errors` |
| Title and body not both empty | Domain constructor | `400 note_content_required` |
| Unknown JSON properties | Jackson `FAIL_ON_UNKNOWN_PROPERTIES` | `400 validation_failed` |
| Color in palette | Enum deserialization | `400 note_color_invalid` |
| Page size ≤ 100 | Pagination parameter binding | `400 validation_failed` |
| ≤ 20 labels per note | Domain invariant | `422 note_label_limit_exceeded` |
| Label name unique per owner | Unique index, translated from the constraint violation | `409 label_name_conflict` |
| Label belongs to caller | Application layer, before attach | `404 label_not_found` — never `403`, so ownership is not disclosed (FR-008) |
| Note belongs to caller | Application layer, on every read and write | `404 note_not_found` (FR-007, FR-008) |
| Mutation of a trashed note | Domain invariant | `409 note_trashed` |
| Concurrent update | `@Version` optimistic lock | `409 note_version_conflict` |

The triple enforcement of length limits is deliberate, not redundant: the boundary check produces a
good error message, the domain invariant keeps the model correct when called from anywhere, and the
database constraint is what actually guarantees no bad row exists.

**Characters, not bytes**: every length limit above counts Unicode characters, never encoded bytes.
`varchar(200)` and `length(body) <= 20000` count characters in PostgreSQL, and Bean Validation's
`@Size` counts them in Java, so the three enforcement points agree. The transport byte ceiling that
produces `413` is a separate, much larger abuse guard and is not a content rule — conflating the two
would reject valid notes in any script that encodes above one byte per character.

**Normative source for the limits**: [spec.md](./spec.md) §Assumptions. The same numbers appear here,
in the Bean Validation annotations, in the SQL constraints, in `contracts/openapi.yaml`'s
`maxLength`, and in the error catalog's messages. Five copies drift, so an automated test asserts all
five agree; if they ever disagree, spec.md is right and the others are defects.

---

## Notes on identity

There is no `users` table. `owner_id` holds the Keycloak `sub` claim directly. A user who has never
created a note simply matches no rows, which is why a valid credential for an unknown user returns an
empty collection rather than an error, as the spec's edge cases require. The consequence to accept:
if the identity provider is ever replaced and `sub` values change, a data migration is needed to
remap ownership.
