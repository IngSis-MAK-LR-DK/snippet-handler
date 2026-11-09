package edu.austral.ingsis.snippethandler.reprocessing;

import edu.austral.ingsis.snippethandler.language.RuleType;

/** What is done to every snippet of a job. */
public enum ReprocessType {
    /** Lint with the current rules of the owner (US 15). */
    LINT,
    /** Format with the current rules of the owner (US 12). */
    FORMAT,
    /** Move to a new version of the language, if the snippet is valid in it. */
    REVALIDATE;

    public static ReprocessType of(RuleType type) {
        return valueOf(type.name());
    }
}
