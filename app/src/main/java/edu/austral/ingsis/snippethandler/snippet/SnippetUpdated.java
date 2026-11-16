package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;

/**
 * The source of a snippet changed (content, language or version). Published as a Spring event
 * inside the update's transaction; listeners run after it commits (US 16).
 */
public record SnippetUpdated(UUID snippetId) {}
