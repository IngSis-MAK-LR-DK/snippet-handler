package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.Pattern;

/** Partial update of a snippet; null fields are left unchanged. */
public record SnippetUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        String description,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String language,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String version,
        String content) {}
