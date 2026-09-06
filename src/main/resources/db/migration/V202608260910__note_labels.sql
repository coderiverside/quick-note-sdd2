-- The Note contract carries `labelIds` from its very first response, so the attachment table
-- exists from the start. The `labels` table itself and the FK to it arrive with the labels story.
CREATE TABLE note_labels (
    note_id  uuid NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    label_id uuid NOT NULL,
    -- The composite key is what makes a duplicate attach a no-op rather than a duplicate row.
    PRIMARY KEY (note_id, label_id)
);

CREATE INDEX ix_note_labels_label ON note_labels (label_id, note_id);
