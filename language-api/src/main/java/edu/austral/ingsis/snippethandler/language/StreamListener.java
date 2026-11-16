package edu.austral.ingsis.snippethandler.language;

import java.util.List;

/** Events of a streamed run, in the order they happen. */
public interface StreamListener {

    /** The snippet printed a line. */
    void output(String line);

    /** The snippet waits for an input; answer with {@link RunningSnippet#input}. */
    void inputRequested(String prompt);

    /** The run ended; {@code errors} is empty if it went well. Called exactly once. */
    void finished(List<String> errors);
}
