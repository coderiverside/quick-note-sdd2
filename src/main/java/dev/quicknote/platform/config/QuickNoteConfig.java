package dev.quicknote.platform.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.smallrye.config.ConfigMapping;

/**
 * Configuration is validated at startup; the process refuses to start on an invalid value rather
 * than failing later, in production, on the first request that happens to need it.
 */
@ConfigMapping(prefix = "quicknote")
public interface QuickNoteConfig {

    Trash trash();

    RateLimit ratelimit();

    interface Trash {
        @Min(1)
        @Max(3650)
        int retentionDays();

        /** When the retention sweep runs. Every property under the prefix must be declared here. */
        @NotBlank
        String purgeCron();
    }

    interface RateLimit {
        @Min(1)
        int perPrincipal();

        @Min(1)
        int perIp();
    }
}
