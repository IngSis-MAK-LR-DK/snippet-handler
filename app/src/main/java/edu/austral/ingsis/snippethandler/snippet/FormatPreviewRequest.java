package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FormatPreviewRequest(
        @NotBlank String language, @NotBlank String version, @NotNull String content) {}
