package edu.austral.ingsis.snippethandler.events;

import java.util.UUID;

/**
 * Reacts to a snippet update published through {@link SnippetEventPublisher}. Handlers run in the
 * background, after the update is committed, and know nothing about how the event travelled.
 */
public interface SnippetUpdateHandler {

    void onSnippetUpdated(UUID snippetId);
}
