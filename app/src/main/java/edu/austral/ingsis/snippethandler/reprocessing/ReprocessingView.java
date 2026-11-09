package edu.austral.ingsis.snippethandler.reprocessing;

import java.time.Instant;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

/**
 * Progress of a reprocessing: how many snippets are done, skipped because they failed, or waiting.
 */
public record ReprocessingView(
        UUID id,
        RuleType type,
        String language,
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
