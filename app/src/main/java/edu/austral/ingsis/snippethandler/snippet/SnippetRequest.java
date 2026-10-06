package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Data needed to create a snippet, whether typed in the editor or uploaded as a file. */
public record SnippetRequest(
        @NotBlank String name,
        String description,
        @NotBlank String language,
        @NotBlank String version,
        @NotNull String content) {}
