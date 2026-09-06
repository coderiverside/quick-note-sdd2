package dev.quicknote.platform.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import dev.quicknote.platform.config.QuickNoteConfig;

/**
 * Token buckets, one per principal and one per source IP.
 *
 * <p><strong>Instance-local by design, for now.</strong> The effective ceiling therefore scales with
 * replica count. This is the deviation recorded in the plan's Complexity Tracking; the interface is
 * shaped so that only the backing store changes when it moves to a gateway or a shared cache — no
 * caller and no filter has to be touched.
 */
@ApplicationScoped
public class RateLimiter {

    /** Bounded so a flood of distinct keys cannot exhaust memory. */
    private static final int MAX_TRACKED_KEYS = 100_000;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Inject
    QuickNoteConfig config;

    private Cache<String, Bucket> principalBuckets;
    private Cache<String, Bucket> ipBuckets;

    @PostConstruct
    void init() {
        principalBuckets = newCache();
        ipBuckets = newCache();
    }

    private static Cache<String, Bucket> newCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    /** The outcome of one attempt, carrying what the response headers need. */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {

        static Decision from(ConsumptionProbe probe) {
            return new Decision(
                    probe.isConsumed(),
                    probe.getRemainingTokens(),
                    Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill())));
        }
    }

    /**
     * Charges both buckets. Whichever is exhausted first denies the request, so neither a single busy
     * user nor a single busy address can crowd out everyone else.
     */
    public Decision check(String principal, String sourceIp) {
        Decision byPrincipal =
                consume(principalBuckets, principal, config.ratelimit().perPrincipal());
        if (!byPrincipal.allowed()) {
            return byPrincipal;
        }
        return consume(ipBuckets, sourceIp, config.ratelimit().perIp());
    }

    private static Decision consume(Cache<String, Bucket> buckets, String key, int perMinute) {
        Bucket bucket = buckets.get(key == null ? "unknown" : key, k -> newBucket(perMinute));
        return Decision.from(bucket.tryConsumeAndReturnRemaining(1));
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, WINDOW))
                .build();
    }
}
