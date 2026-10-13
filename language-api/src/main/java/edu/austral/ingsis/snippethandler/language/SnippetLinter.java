package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public interface SnippetLinter {

    /** Evaluates the source against the given lint rules. */
    List<Violation> lint(String source, String version, List<RuleConfig> rules);
}
