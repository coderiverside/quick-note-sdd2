-- Labels, and the foreign key that ties the attachment table to them.
CREATE TABLE labels (
    id         uuid         PRIMARY KEY,
    owner_id   varchar(255) NOT NULL,
    name       varchar(50)  NOT NULL,
    created_at timestamptz  NOT NULL,

    CONSTRAINT ck_labels_name_not_blank CHECK (length(trim(name)) > 0)
);

-- Uniqueness enforced in the database, not merely in the application: a check-then-insert in code
-- loses a race between two concurrent creates, and this cannot.
CREATE UNIQUE INDEX ux_labels_owner_name ON labels (owner_id, lower(trim(name)));

-- Deleting a label detaches it from every note and deletes no note.
ALTER TABLE note_labels
    ADD CONSTRAINT fk_note_labels_label
    FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE;
