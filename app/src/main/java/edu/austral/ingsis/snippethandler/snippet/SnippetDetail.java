package edu.austral.ingsis.snippethandler.snippet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.Violation;

public record SnippetDetail(
        UUID id,
        String name,
        String description,
        String language,
        String version,
        String ownerId,
        Compliance compliance,
        List<Violation> lintViolations,
        String content,
        Instant createdAt,
        Instant updatedAt) {

    public static SnippetDetail of(Snippet snippet, String content) {
        return new SnippetDetail(
                snippet.getId(),
                snippet.getName(),
                snippet.getDescription(),
                snippet.getLanguage(),
                snippet.getLanguageVersion(),
                snippet.getOwnerId(),
                snippet.getCompliance(),
                snippet.getLintViolations(),
                content,
                snippet.getCreatedAt(),
                snippet.getUpdatedAt());
    }
}
