-- Replay protection for note creation, the only non-idempotent operation in the contract.
CREATE TABLE idempotency_keys (
    owner_id        varchar(255) NOT NULL,
    key             varchar(255) NOT NULL,
    note_id         uuid         NOT NULL,
    response_status smallint     NOT NULL,
    created_at      timestamptz  NOT NULL,
    -- Scoped per owner, so one user cannot probe or collide with another's keys.
    PRIMARY KEY (owner_id, key)
);

CREATE INDEX ix_idempotency_created_at ON idempotency_keys (created_at);
