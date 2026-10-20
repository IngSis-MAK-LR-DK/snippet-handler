package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public interface RuleCatalog {

    /** Rules the language supports out of the box, with their default configuration. */
    List<RuleDefinition> defaultRules(RuleType type);
}
