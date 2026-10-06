package edu.austral.ingsis.snippethandler.events;

import java.util.UUID;

/**
 * Notifies that something happened to a snippet so slow work (running tests, and later bulk
 * lint/format) runs outside the request. Today it is in-process; it can be backed by a message
 * broker without touching the callers.
 */
public interface SnippetEventPublisher {

    void snippetUpdated(UUID snippetId);
}
