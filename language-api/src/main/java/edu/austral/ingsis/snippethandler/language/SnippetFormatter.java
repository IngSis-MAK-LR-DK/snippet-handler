package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public interface SnippetFormatter {

    /** Returns the source formatted according to the given format rules. */
    String format(String source, String version, List<RuleConfig> rules);
}
