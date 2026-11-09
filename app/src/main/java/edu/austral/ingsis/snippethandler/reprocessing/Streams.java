package edu.austral.ingsis.snippethandler.reprocessing;

/** Names of the streams and of the consumer group of this service. */
public final class Streams {

    /** One message per rule change: {@link RulesChanged}. */
    public static final String RULES_CHANGED = "rules-changed";

    /** One message per snippet to reprocess: the id of its {@link ReprocessTask}. */
    public static final String SNIPPET_REPROCESS = "snippet-reprocess";

    public static final String GROUP = "snippet-handler";

    private Streams() {}
}
