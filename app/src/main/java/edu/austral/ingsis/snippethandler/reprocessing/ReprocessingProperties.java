package edu.austral.ingsis.snippethandler.reprocessing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration under {@code snippet.reprocessing}.
 *
 * @param enabled whether this instance consumes the streams
 * @param maxAttempts tries per snippet before it is skipped and counted as failed
 * @param claimIdle how long a delivered message may stay unacknowledged before it is retried
 * @param pollInterval wait between polls when the streams are empty
 */
@ConfigurationProperties("snippet.reprocessing")
public record ReprocessingProperties(
        Boolean enabled, Integer maxAttempts, Duration claimIdle, Duration pollInterval) {

    public ReprocessingProperties {
        enabled = enabled == null || enabled;
        maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        claimIdle = claimIdle == null ? Duration.ofSeconds(30) : claimIdle;
        pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
    }
}
