package dev.quicknote.platform.scheduler;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import dev.quicknote.notes.application.TrashService;
import dev.quicknote.shared.idempotency.IdempotencyStore;

/**
 * Retention, without user action.
 *
 * <p>Backed by Quartz's clustered job store: a plain scheduled method fires on every instance at
 * once, so with more than one replica the sweep would run several times over. Clustering makes it
 * exactly once. Deletion happens in bounded batches so a long lock never forms on the notes table.
 */
@ApplicationScoped
public class RetentionJob {

    /** Bounded so one sweep cannot lock the table for an unbounded stretch. */
    static final int BATCH_SIZE = 500;

    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final Logger LOG = Logger.getLogger(RetentionJob.class);

    @Inject
    TrashService trash;

    @Inject
    IdempotencyStore idempotencyKeys;

    @Scheduled(cron = "{quicknote.trash.purge-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        long purgedNotes = purgeExpiredNotes();
        long purgedKeys = idempotencyKeys.purgeExpired(Instant.now());
        if (purgedNotes > 0 || purgedKeys > 0) {
            LOG.infof(
                    "retention sweep complete [notesPurged=%d idempotencyKeysPurged=%d retentionDays=%d]",
                    purgedNotes, purgedKeys, trash.retentionDays());
        }
    }

    /** Exposed for the retention test, which must be able to run a sweep on demand. */
    public long purgeExpiredNotes() {
        long total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            long removed = trash.purgeExpired(BATCH_SIZE);
            total += removed;
            if (removed < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
