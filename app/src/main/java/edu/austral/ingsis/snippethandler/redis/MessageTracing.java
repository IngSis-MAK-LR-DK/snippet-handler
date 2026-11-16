package edu.austral.ingsis.snippethandler.redis;

import java.util.Map;

/**
 * Carries the trace of whoever publishes a message to whoever handles it. Without it the trace ends
 * at the stream: a change of rules and the reprocessing it causes would look unrelated.
 */
public interface MessageTracing {

    /** Prefix of the fields that carry the trace, so they never clash with the message's own. */
    String PREFIX = "trace.";

    /** No tracing: messages travel as they are. */
    MessageTracing NONE =
            new MessageTracing() {
                @Override
                public Map<String, String> outgoing() {
                    return Map.of();
                }

                @Override
                public void handling(String stream, Map<String, String> fields) {}
            };

    /** Fields to add to a message published now, from the current trace (already prefixed). */
    Map<String, String> outgoing();

    /** Called when handling a message: continues the trace it carries, if any. */
    void handling(String stream, Map<String, String> fields);
}
