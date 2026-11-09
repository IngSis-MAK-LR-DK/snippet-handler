package edu.austral.ingsis.snippethandler.reprocessing;

/** Names of the streams and of the consumer group of this service. */
public final class Streams {

    /** One message per request to reprocess snippets: {@link ReprocessRequest}. */
    public static final String REQUESTS = "reprocess-requests";

    /** One message per snippet to reprocess: the id of its {@link ReprocessTask}. */
    public static final String SNIPPET_REPROCESS = "snippet-reprocess";

    public static final String GROUP = "snippet-handler";

    private Streams() {}
}
