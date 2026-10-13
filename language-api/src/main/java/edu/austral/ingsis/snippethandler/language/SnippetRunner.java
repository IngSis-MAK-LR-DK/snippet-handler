package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public interface SnippetRunner {

    /** Runs the source feeding {@code inputs} in order and collecting every printed line. */
    ExecutionResult execute(String source, String version, List<String> inputs);
}
