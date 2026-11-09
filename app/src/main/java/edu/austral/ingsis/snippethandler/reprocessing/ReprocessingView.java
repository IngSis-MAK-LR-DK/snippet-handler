package edu.austral.ingsis.snippethandler.reprocessing;

import java.time.Instant;
import java.util.UUID;

/**
 * Progress of a reprocessing: how many snippets are done, skipped because they failed, or waiting.
 *
 * @param version target language version, only for revalidations
 */
public record ReprocessingView(
        UUID id,
        ReprocessType type,
        String language,
        String version,
        Status status,
        long total,
        long done,
        long failed,
        long pending,
        Instant createdAt) {

    public enum Status {
        RUNNING,
        COMPLETED
    }
}
