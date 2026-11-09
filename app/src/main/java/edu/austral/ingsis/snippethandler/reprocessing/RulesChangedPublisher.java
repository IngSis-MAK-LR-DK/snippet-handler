package edu.austral.ingsis.snippethandler.reprocessing;

import edu.austral.ingsis.snippethandler.language.RuleType;

/** Tells the rest of the system that a user changed their rules. */
public interface RulesChangedPublisher {

    void rulesChanged(String userId, RuleType type, String language);
}
