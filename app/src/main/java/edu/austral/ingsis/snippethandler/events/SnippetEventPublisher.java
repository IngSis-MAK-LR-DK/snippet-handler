package edu.austral.ingsis.snippethandler.events;

import java.util.UUID;

/**
 * Notifies that something happened to a snippet so slow work (running tests, and later bulk
 * lint/format) runs outside the request. Whoever is interested implements {@link
 * SnippetUpdateHandler}. Transport is in-process today; moving it to a message broker only means
 * replacing the classes of this package.
 */
public interface SnippetEventPublisher {

    void snippetUpdated(UUID snippetId);
}
