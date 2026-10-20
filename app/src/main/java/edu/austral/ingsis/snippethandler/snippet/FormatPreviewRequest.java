package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.NotNull;

/**
 * Source to format without storing it (the editor's content). Language and version are optional:
 * the default language and its latest version are used when left out.
 */
public record FormatPreviewRequest(@NotNull String content, String language, String version) {}
