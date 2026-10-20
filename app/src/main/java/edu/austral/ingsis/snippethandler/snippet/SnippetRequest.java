package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data needed to create a snippet, whether typed in the editor or uploaded as a file. A missing
 * version means the latest version of the language.
 */
public record SnippetRequest(
        @NotBlank String name,
        String description,
        @NotBlank String language,
        String version,
        @NotNull String content) {}
