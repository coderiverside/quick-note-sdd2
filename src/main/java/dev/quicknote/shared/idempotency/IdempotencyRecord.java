package dev.quicknote.shared.idempotency;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A recorded creation, so a retried request returns the original outcome instead of a second note. */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyRecord.Key.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "owner_id", nullable = false)
    public String ownerId;

    @Id
    @Column(name = "key", nullable = false)
    public String key;

    @Column(name = "note_id", nullable = false)
    public UUID noteId;

    @Column(name = "response_status", nullable = false)
    public short responseStatus;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Composite identity: keys are scoped per owner so users cannot collide or probe. */
    public static class Key implements Serializable {
        public String ownerId;
        public String key;

        public Key() {}

        public Key(String ownerId, String key) {
            this.ownerId = ownerId;
            this.key = key;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key k)) {
                return false;
            }
            return Objects.equals(ownerId, k.ownerId) && Objects.equals(key, k.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerId, key);
        }
    }
}
