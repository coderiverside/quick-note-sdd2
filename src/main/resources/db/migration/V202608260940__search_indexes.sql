-- Substring search means a leading-wildcard ILIKE, which no B-tree can serve. Trigram GIN indexes
-- are the access path that makes '%term%' indexable at all; without them every search is a
-- sequential scan, which the constitution treats as a defect rather than a tuning opportunity.
CREATE INDEX ix_notes_title_trgm ON notes USING gin (title gin_trgm_ops);
CREATE INDEX ix_notes_body_trgm  ON notes USING gin (body  gin_trgm_ops);

-- The retention sweep only ever looks at trashed rows, so the index only covers those.
CREATE INDEX ix_notes_trashed_at ON notes (trashed_at) WHERE state = 'TRASHED';
