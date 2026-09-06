-- Quick Note — initial notes schema.
-- Timestamp-versioned so concurrent story branches cannot collide on a version number.

-- Indexable substring search needs trigram support; a leading-wildcard ILIKE cannot use a B-tree.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE notes (
    id             uuid         PRIMARY KEY,
    owner_id       varchar(255) NOT NULL,
    title          varchar(200),
    body           text,
    color          varchar(16)  NOT NULL DEFAULT 'default',
    pinned         boolean      NOT NULL DEFAULT false,
    state          varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    previous_state varchar(16),
    trashed_at     timestamptz,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,

    -- These mirror the domain invariants. The domain keeps the model correct when called from
    -- anywhere; these are what actually guarantee no bad row can exist.
    CONSTRAINT ck_notes_content     CHECK (title IS NOT NULL OR body IS NOT NULL),
    CONSTRAINT ck_notes_body_len    CHECK (body IS NULL OR length(body) <= 20000),
    CONSTRAINT ck_notes_pinned_active CHECK (NOT pinned OR state = 'ACTIVE'),
    CONSTRAINT ck_notes_trashed_at  CHECK ((state = 'TRASHED') = (trashed_at IS NOT NULL)),
    CONSTRAINT ck_notes_state       CHECK (state IN ('ACTIVE', 'ARCHIVED', 'TRASHED')),
    CONSTRAINT ck_notes_prev_state  CHECK (previous_state IS NULL OR previous_state IN ('ACTIVE', 'ARCHIVED'))
);

-- Serves the default and filtered listings, pinned first then most recently updated.
CREATE INDEX ix_notes_owner_listing
    ON notes (owner_id, state, pinned DESC, updated_at DESC, id DESC);
