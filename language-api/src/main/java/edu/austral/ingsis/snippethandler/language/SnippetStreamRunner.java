package edu.austral.ingsis.snippethandler.language;

/**
 * Runs a snippet while it is being evaluated (US 9 and 10): every printed line and every input it
 * asks for are reported as they happen, instead of once at the end.
 */
public interface SnippetStreamRunner {

    /**
     * Starts the run and returns right away; events reach {@code listener} from another thread,
     * ending with exactly one {@link StreamListener#finished}.
     *
     * @throws LanguageServiceException if the run could not even start
     */
    RunningSnippet stream(StreamRequest request, StreamListener listener);
}
