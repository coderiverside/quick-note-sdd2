package dev.quicknote.shared.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

/** Where recorded creations live. Entries expire; the retention job sweeps them. */
@ApplicationScoped
public class IdempotencyStore implements PanacheRepositoryBase<IdempotencyRecord, IdempotencyRecord.Key> {

    /** How long a replay returns the original response. Long enough for any sane client retry. */
    public static final Duration WINDOW = Duration.ofHours(24);

    public Optional<IdempotencyRecord> find(String ownerId, String key) {
        return find("ownerId = ?1 and key = ?2", ownerId, key).firstResultOptional();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(String ownerId, String key, UUID noteId, int status) {
        if (find(ownerId, key).isPresent()) {
            return;
        }
        IdempotencyRecord entry = new IdempotencyRecord();
        entry.ownerId = ownerId;
        entry.key = key;
        entry.noteId = noteId;
        entry.responseStatus = (short) status;
        entry.createdAt = Instant.now();
        persist(entry);
    }

    @Transactional
    public long purgeExpired(Instant now) {
        return delete("createdAt < ?1", now.minus(WINDOW));
    }
}
