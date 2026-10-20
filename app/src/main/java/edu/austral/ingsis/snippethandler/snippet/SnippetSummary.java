package edu.austral.ingsis.snippethandler.snippet;

import java.time.Instant;
import java.util.UUID;

public record SnippetSummary(
        UUID id,
        String name,
        String description,
        String language,
        String version,
        String ownerId,
        Compliance compliance,
        Instant createdAt,
        Instant updatedAt) {

    public static SnippetSummary of(Snippet snippet) {
        return new SnippetSummary(
                snippet.getId(),
                snippet.getName(),
                snippet.getDescription(),
                snippet.getLanguage(),
                snippet.getLanguageVersion(),
                snippet.getOwnerId(),
                snippet.getCompliance(),
                snippet.getCreatedAt(),
                snippet.getUpdatedAt());
    }
}
