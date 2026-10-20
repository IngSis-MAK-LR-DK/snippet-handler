package edu.austral.ingsis.snippethandler.snippet;

import java.util.List;

import edu.austral.ingsis.snippethandler.language.Violation;

/** Outcome of linting a snippet with the rules of its owner. */
public record LintReport(Compliance compliance, List<Violation> violations) {

    public LintReport {
        violations = List.copyOf(violations);
    }

    public static LintReport of(List<Violation> violations) {
        return new LintReport(
                violations.isEmpty() ? Compliance.COMPLIANT : Compliance.NOT_COMPLIANT, violations);
    }

    /** The snippet could not be linted (e.g. the language adapter is down). */
    public static LintReport failed() {
        return new LintReport(Compliance.FAILED, List.of());
    }
}
