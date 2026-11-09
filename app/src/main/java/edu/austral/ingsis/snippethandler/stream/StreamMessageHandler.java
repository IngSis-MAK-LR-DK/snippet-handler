package edu.austral.ingsis.snippethandler.stream;

import java.util.Map;

/**
 * Processes one message of a stream. Returning normally acknowledges it; throwing leaves it pending
 * so it is delivered again. Delivery is at least once, so handlers must be idempotent.
 */
@FunctionalInterface
public interface StreamMessageHandler {

    void handle(Map<String, String> fields);
}
