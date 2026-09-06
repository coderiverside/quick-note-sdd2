# Feature Specification: Quick Note — Note Management API

**Feature Branch**: `001-note-management-api`

**Created**: 2026-08-25

**Status**: Draft

**Input**: User description: "Create the specification to build a REST API for a note-taking service similar to Google Keep with name quick-note. A service where an authenticated user can create and manage personal notes. Each note has a title, text content, color, pinned status, archived status, and trashed status. Users can organize notes using custom labels. To allow users to quickly capture, organize, find, and manage ideas and tasks."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture and revisit a note (Priority: P1)

A signed-in user has an idea or task they need to record. They capture it as a note with a title
and body text, and it is immediately saved to their personal collection. Later they open their
collection, see the note, read it, change its wording, and save the change. When the note is no
longer useful they remove it from view.

**Why this priority**: This is the irreducible core of the product. Without capture, retrieval, and
edit, nothing else has anything to operate on. Shipping only this story yields a usable
single-purpose notepad service.

**Independent Test**: Create a note, list the collection and confirm the note appears, retrieve it
by its identifier, update its title and body, confirm the change persists across a fresh retrieval,
then remove it and confirm it no longer appears in the default collection view.

**Acceptance Scenarios**:

1. **Given** an authenticated user with an empty collection, **When** they create a note with a
   title and body, **Then** the note is stored, assigned a unique identifier, and returned with its
   creation and last-updated timestamps.
2. **Given** an authenticated user with several notes, **When** they request their collection,
   **Then** only their own notes are returned, and notes belonging to other users are never included.
3. **Given** an existing note, **When** the owner updates its title or body, **Then** the new values
   are persisted and the last-updated timestamp advances.
4. **Given** an existing note, **When** a user who does not own it attempts to retrieve or modify it,
   **Then** the request is refused and no note content is disclosed.
5. **Given** a note created with a body but no title, **When** it is saved, **Then** it is accepted,
   because a title is optional.
6. **Given** a request to create a note with neither a title nor a body, **When** it is submitted,
   **Then** it is rejected with a validation error explaining that at least one of the two is required.

---

### User Story 2 - Organize the collection with pin, color, and archive (Priority: P2)

A user whose collection has grown wants the important notes to surface first and the finished ones
to get out of the way. They pin the notes that matter right now, assign colors to group related
notes visually, and archive notes they want to keep but no longer see day to day.

**Why this priority**: Organization is what separates a note service from a text file. It becomes
valuable as soon as a user has more than a handful of notes, but it is meaningless before capture
exists.

**Independent Test**: Create several notes, pin two of them, archive one, set a color on another,
then list the collection and confirm pinned notes are ordered first, the archived note is absent
from the default view but present in the archive view, and the color is returned with the note.

**Acceptance Scenarios**:

1. **Given** a collection of notes, **When** the user pins a note, **Then** it is marked pinned and
   appears ahead of all unpinned notes in the default collection ordering.
2. **Given** a pinned note, **When** the user unpins it, **Then** it returns to the ordinary ordering
   position determined by its last-updated time.
3. **Given** an active note, **When** the user archives it, **Then** it is excluded from the default
   collection view and included in the archived view.
4. **Given** an archived note, **When** the user unarchives it, **Then** it returns to the default
   collection view.
5. **Given** a note, **When** the user sets its color to a value from the supported palette, **Then**
   the color is persisted and returned on subsequent retrievals.
6. **Given** a note, **When** the user sets a color outside the supported palette, **Then** the
   request is rejected with a validation error listing the permitted values.
7. **Given** an unarchived note that is pinned, **When** the user archives it, **Then** the note is
   archived and its pinned status is cleared, because archived notes are not pinned.

---

### User Story 3 - Organize with custom labels (Priority: P3)

A user wants to group notes by topic in a way they define themselves — "work", "recipes",
"reading list". They create labels, attach one or more to any note, and later view every note
carrying a given label. They rename a label when their vocabulary changes and delete one they no
longer use, without losing the notes it was attached to.

**Why this priority**: Labels are the primary organizing mechanism named in the product brief and
the main differentiator from a flat list, but the collection is still usable without them.

**Independent Test**: Create two labels, attach both to one note and one to another, list notes
filtered by each label and confirm the correct membership, rename a label and confirm the notes
still resolve under the new name, then delete a label and confirm the notes survive with the label
detached.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a label with a name, **Then** the label is
   stored in their personal label set and returned with a unique identifier.
2. **Given** a user who already has a label named "work", **When** they create another label named
   "work", **Then** the request is rejected with a conflict error, because label names are unique
   per user.
3. **Given** an existing note and an existing label, **When** the user attaches the label to the
   note, **Then** the note reports that label among its labels.
4. **Given** a note carrying a label, **When** the user detaches the label, **Then** the note no
   longer reports it and both the note and the label continue to exist.
5. **Given** several labelled notes, **When** the user lists notes filtered by a label, **Then** only
   notes carrying that label are returned.
6. **Given** an existing label, **When** the user renames it, **Then** every note carrying it
   reflects the new name without any change to note content.
7. **Given** a label attached to notes, **When** the user deletes the label, **Then** the label is
   removed from every note that carried it and no note is deleted.
8. **Given** a note, **When** the user attempts to attach a label belonging to another user, **Then**
   the request is refused and the label is not attached.

---

### User Story 4 - Find a note quickly (Priority: P4)

A user remembers a phrase from a note but not where it is. They search their collection by that
text and get back the matching notes, and they can narrow the collection by state — active,
archived, pinned, or a particular label — to get to the right note without scrolling.

**Why this priority**: Findability is what keeps the service useful past a few dozen notes, but it
depends on capture, organization, and labelling already being in place.

**Independent Test**: Create notes with distinct words in titles and bodies, search for a term
appearing only in one body, and confirm exactly that note is returned; then combine a search term
with a label filter and confirm the intersection is returned.

**Acceptance Scenarios**:

1. **Given** notes containing a term in the title and other notes containing it in the body, **When**
   the user searches for that term, **Then** both sets are returned.
2. **Given** a search term differing only in letter case from the stored text, **When** the user
   searches, **Then** the matching notes are still returned, because search is case-insensitive.
3. **Given** a search that matches nothing, **When** it is executed, **Then** an empty result set is
   returned rather than an error.
4. **Given** notes in the trash containing the search term, **When** the user searches without
   explicitly requesting trashed notes, **Then** trashed notes are excluded from the results.
5. **Given** a collection larger than one page, **When** the user requests it, **Then** results are
   returned one page at a time with a means of retrieving the next page.
6. **Given** a request for a page size above the enforced maximum, **When** it is submitted, **Then**
   it is rejected with a validation error stating the maximum.

---

### User Story 5 - Recover from mistakes with the trash (Priority: P5)

A user deletes a note and then realizes they still need it. Deleted notes go to a trash area where
they remain recoverable for a limited period. The user restores the note, or empties the trash
deliberately when they are certain, and anything left in the trash past the retention window is
purged automatically.

**Why this priority**: This is a safety net that protects the value created by every earlier story.
It matters, but a first release is demonstrable without it.

**Independent Test**: Delete a note, confirm it is absent from the default view and present in the
trash view, restore it and confirm it returns to the default view intact, then delete another note
and permanently remove it and confirm it is unrecoverable.

**Acceptance Scenarios**:

1. **Given** an active note, **When** the user deletes it, **Then** it is marked trashed, removed
   from the default and archived views, and appears in the trash view with the time it was trashed.
2. **Given** a trashed note, **When** the user restores it, **Then** it returns to the collection with
   its title, body, color, and labels unchanged.
3. **Given** a trashed note, **When** the user permanently deletes it, **Then** it is irrecoverably
   removed and subsequent requests for it report that it does not exist.
4. **Given** several trashed notes, **When** the user empties the trash, **Then** every trashed note
   is permanently removed and no active or archived note is affected.
5. **Given** a note that has been in the trash beyond the retention window, **When** the retention
   process runs, **Then** the note is permanently removed without user action.
6. **Given** a trashed note, **When** the user attempts to edit its content, pin it, or archive it,
   **Then** the request is refused with an explanation that the note must be restored first.
7. **Given** a pinned note, **When** the user deletes it, **Then** it is trashed and its pinned status
   is cleared.

---

### Edge Cases

- **Empty note**: A submission with neither title nor body is rejected; a note with only a title or
  only a body is accepted.
- **Oversized content**: A title beyond the maximum length or a body beyond the maximum size is
  rejected with a validation error naming the limit, rather than being silently truncated.
- **Duplicate label attachment**: Attaching a label a note already carries succeeds without creating
  a duplicate entry; the note's label set is unchanged.
- **Detaching an unattached label**: Detaching a label the note does not carry succeeds without
  error, so the operation is repeatable.
- **Label limit**: Attaching labels beyond the per-note maximum is rejected with a validation error.
- **Conflicting states**: A note is never simultaneously archived and pinned, and never
  simultaneously trashed and pinned; the system clears the conflicting flag as part of the
  transition rather than rejecting it. Archived and trashed are likewise mutually exclusive — a note
  is in exactly one of three states, active, archived, or trashed — so trashing an archived note
  leaves it trashed, and restoring it returns it to archived.
- **Nonexistent identifiers**: Requests naming a note or label that does not exist are refused
  identically to requests for another user's note or label, so existence is not disclosed.
- **Malformed identifiers**: An identifier that is not a well-formed value is rejected as malformed
  before any lookup occurs.
- **Concurrent edits**: When two updates to the same note arrive close together, the collection
  remains internally consistent and the last-updated timestamp reflects the surviving state.
- **Repeated deletion**: Deleting a note already in the trash does not create a second trash entry
  and does not change the original trashed timestamp.
- **Restoring into a deleted label**: Restoring a note whose label was deleted while it sat in the
  trash succeeds, and the note returns without that label.
- **Whitespace-only values**: A title, body, or label name consisting only of whitespace is treated
  as empty for validation purposes.
- **Label name casing**: Label names differing only by case or surrounding whitespace are treated as
  the same name for the uniqueness rule.
- **Unauthenticated access**: Any request without a credential is refused before any note or label
  data is read.
- **Expired or malformed credential**: A credential that has expired, is structurally malformed, or
  fails integrity verification is refused as unauthenticated, and the response does not reveal which
  check failed.
- **Credential for an unknown user**: A validly signed credential naming a user with no existing
  collection yields an empty collection rather than an error, so a first-time user is not blocked.
- **Identity supplied out of band**: A user identifier supplied in the request body, query string, or
  a header outside the credential is ignored; the credential is the only source of identity.

## Requirements *(mandatory)*

### Functional Requirements

**Access and ownership**

- **FR-001**: System MUST require a valid credential on every operation; requests without one MUST be
  refused without disclosing whether the requested resource exists.
- **FR-002**: System MUST verify the presented credential — its integrity, its issuer, its intended
  audience, and its expiry — before performing any read or write, and MUST refuse the request when
  any check fails.
- **FR-003**: System MUST refuse an expired credential, a malformed credential, and a credential
  whose integrity cannot be verified, distinguishing "not authenticated" from "not permitted" in the
  response, without revealing which specific verification check failed.
- **FR-004**: System MUST derive the acting user's identity solely from the verified credential, and
  MUST NOT accept a user identity supplied anywhere else in the request.
- **FR-005**: System MUST NOT issue, refresh, or revoke credentials, and MUST NOT store user
  passwords; credential issuance is the responsibility of the external identity provider this
  service verifies against.
- **FR-006**: System MUST publish the credential contract it verifies — the expected issuer,
  audience, signing method, and the claim carrying the user identifier — as part of its interface
  documentation.
- **FR-007**: System MUST scope every note and label to the user who created it, and MUST refuse any
  attempt by one user to read, modify, or delete another user's note or label.
- **FR-008**: System MUST respond identically to a request for a resource that does not exist and a
  request for a resource owned by another user, so that resource existence is not disclosed.

**Note lifecycle**

- **FR-009**: Users MUST be able to create a note with an optional title, optional text body,
  optional color, and optional set of labels.
- **FR-010**: System MUST reject a note that has neither a title nor a body.
- **FR-011**: System MUST assign every note a unique identifier, a creation timestamp, and a
  last-updated timestamp, and MUST advance the last-updated timestamp on every content or state
  change.
- **FR-012**: Users MUST be able to retrieve a single note by its identifier and to list their notes.
- **FR-013**: Users MUST be able to update a note's title, body, and color, individually or together.
- **FR-014**: System MUST reject a title longer than 200 characters and a body longer than 20,000
  characters, naming the exceeded limit in the error.
- **FR-015**: System MUST treat titles, bodies, and label names consisting only of whitespace as
  empty for the purposes of validation.

**Note state**

- **FR-016**: Users MUST be able to pin and unpin a note.
- **FR-017**: Users MUST be able to archive and unarchive a note.
- **FR-018**: Users MUST be able to set a note's color to any value in the supported palette, and
  System MUST reject values outside it.
- **FR-019**: System MUST default a new note to unpinned, unarchived, untrashed, and the default
  color.
- **FR-020**: System MUST clear a note's pinned status when the note is archived or trashed, so that
  pinned never coexists with archived or trashed.
- **FR-021**: System MUST refuse content edits, pin changes, archive changes, and label changes on a
  trashed note, directing the user to restore it first.

**Trash**

- **FR-022**: Users MUST be able to delete a note, which MUST move it to the trash rather than
  removing it, and MUST record the time it was trashed.
- **FR-023**: Users MUST be able to list trashed notes, restore a trashed note to its previous
  active or archived state, permanently delete a single trashed note, and empty the trash entirely.
- **FR-024**: System MUST permanently remove notes that have been in the trash longer than 30 days,
  without requiring user action.
- **FR-025**: System MUST make permanent deletion irreversible and MUST report subsequent requests
  for a permanently deleted note as not found.

**Labels**

- **FR-026**: Users MUST be able to create, list, rename, and delete labels within their own label
  set.
- **FR-027**: System MUST enforce that label names are unique per user, compared case-insensitively
  and ignoring surrounding whitespace, and MUST reject a duplicate with a conflict error.
- **FR-028**: System MUST reject a label name that is empty or longer than 50 characters.
- **FR-029**: Users MUST be able to attach labels to and detach labels from a note, and System MUST
  treat attaching an already-attached label and detaching an unattached label as successful
  no-op operations.
- **FR-030**: System MUST limit a note to at most 20 labels and reject attachments beyond that limit.
- **FR-031**: System MUST detach a deleted label from every note that carried it, and MUST NOT delete
  any note as a consequence of deleting a label.
- **FR-032**: System MUST reject any attempt to attach a label the requesting user does not own.

**Listing, filtering, and search**

- **FR-033**: System MUST exclude archived and trashed notes from the default note listing.
- **FR-034**: Users MUST be able to list notes filtered by state — active, archived, or trashed — and
  by label.
- **FR-035**: System MUST order the default listing with pinned notes first, and within each group by
  last-updated time, most recent first.
- **FR-036**: Users MUST be able to search their notes by a text term matched case-insensitively
  against both title and body, returning notes matching in either.
- **FR-037**: System MUST exclude trashed notes from search results unless trashed notes are
  explicitly requested.
- **FR-038**: System MUST return an empty result set, not an error, when a listing or search matches
  nothing.
- **FR-039**: System MUST paginate every listing and search response with a documented default page
  size of 25 and an enforced maximum of 100, rejecting larger requested page sizes with a validation
  error.
- **FR-040**: System MUST allow filters, search terms, and pagination to be combined in a single
  request, applying them together.

**Validation and errors**

- **FR-041**: System MUST validate every input at the boundary and reject invalid input before any
  change is made, identifying each offending field and the reason.
- **FR-042**: System MUST preserve note and label text exactly as submitted, including punctuation,
  line breaks, and non-Latin characters, and MUST return it unchanged.
- **FR-043**: System MUST NOT expose internal diagnostic detail in any error returned to a user.

### Key Entities *(include if feature involves data)*

- **User**: The owner of a collection. Every note and label belongs to exactly one user, and a user
  sees only their own. Identified by a stable identifier carried on every authenticated request.
- **Note**: A single captured item. Attributes: unique identifier, optional title, optional text
  body, color drawn from the supported palette, pinned flag, archived flag, trashed flag, the time
  it was trashed (when applicable), creation time, last-updated time, owning user, and the set of
  labels attached to it. A note is in exactly one of three states — active, archived, or trashed.
- **Label**: A user-defined tag for grouping notes. Attributes: unique identifier, name unique within
  the owning user's set, owning user, creation time. A label may be attached to many notes.
- **Note–Label attachment**: The many-to-many association between a note and a label. A note carries
  at most 20 labels; a label may carry any number of notes. Deleting either side removes the
  association without deleting the other side.
- **Color palette**: The closed set of named colors a note may take. A note always has exactly one
  color, defaulting to the neutral value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can capture a new note and confirm it is saved in under 5 seconds from starting
  the request.
- **SC-002**: A user can retrieve their collection of 1,000 notes and see the first page of results
  in under 1 second at the 95th percentile.
- **SC-003**: A user searching for a term they remember finds the intended note within the first page
  of results in at least 95% of searches where the term appears in the note.
- **SC-004**: 100% of attempts to read or modify another user's note or label are refused, verified
  by an authorization test covering every operation the service exposes.
- **SC-005**: A note deleted by mistake can be fully restored, with title, body, color, and labels
  intact, at any point within 30 days, in 100% of cases.
- **SC-006**: The service sustains 500 concurrent users performing mixed capture, listing, and search
  activity without response times exceeding the targets in SC-002.
- **SC-007**: 100% of invalid submissions are rejected with an error that names the offending field,
  and no invalid submission results in a stored change.
- **SC-008**: A user new to the service can capture their first note, label it, and find it again by
  search using only the published interface documentation, without external assistance.
- **SC-009**: No note or label is ever returned to a user who does not own it, verified across the
  full test suite with zero exceptions.
- **SC-010**: Every note text submitted, including non-Latin scripts and multi-line content, is
  returned byte-for-byte identical to what was submitted.

## Assumptions

- **Credentials are verified here, issued elsewhere**: This service verifies the credential
  presented on each request and derives the acting user from it, but does not perform account
  registration, sign-in, credential recovery, or session management. Those belong to an external
  identity provider. The service depends on that provider publishing the verification material and
  claim structure it needs; establishing that provider is a prerequisite for running this service,
  not a deliverable of this feature.
- **Personal notes only**: Notes are private to their owner. Sharing, collaboration, multi-user
  editing, and public links are out of scope for this feature.
- **Text notes only**: Checklists, reminders, drawings, images, audio, and file attachments — present
  in comparable products — are out of scope. A note's content is plain text.
- **Colors are a fixed palette**: Colors are chosen from a closed, named set rather than arbitrary
  values, which keeps them portable across clients and validatable at the boundary. The palette is
  the eleven values common to comparable products: default, red, orange, yellow, green, teal, blue,
  dark blue, purple, pink, and brown.
- **Trash retention is 30 days**: Chosen as a conservative industry-standard window. Trashed notes
  are recoverable for that period and purged automatically thereafter.
- **No version history**: Editing a note overwrites its content. Prior revisions are not retained and
  cannot be recovered.
- **Ordering default**: Notes are ordered pinned-first then most-recently-updated-first. User-defined
  manual ordering is out of scope.
- **Search is substring matching**: Search matches a case-insensitive substring of title or body.
  Stemming, fuzzy matching, relevance ranking, and search across labels are out of scope for this
  feature.
- **No soft delete for labels**: Deleting a label is immediate and permanent; labels do not go to the
  trash.
- **Bulk operations are limited to emptying the trash**: Bulk archive, bulk label, and bulk delete of
  arbitrary selections are out of scope for this feature.
- **Limits**: Title 200 characters, body 20,000 characters, label name 50 characters, 20 labels per
  note, page size default 25 and maximum 100. These are conservative defaults chosen to bound
  resource use; they are stated so they are testable and may be revised with evidence.
- **No export or import**: Bulk export of a collection and import from other services are out of
  scope.
