package edu.austral.ingsis.snippethandler.language.http;

import java.util.List;

import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.Violation;

/** Wire format of the language adapter HTTP contract (see docs/language-adapter-contract.md). */
final class AdapterMessages {

    private AdapterMessages() {}

    record ValidateRequest(String content, String version) {}

    record LintRequest(String content, String version, List<RuleConfig> rules) {
        LintRequest {
            rules = List.copyOf(rules);
        }
    }

    record LintResponse(List<Violation> violations) {
        LintResponse {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }

    record FormatRequest(String content, String version, List<RuleConfig> rules) {
        FormatRequest {
            rules = List.copyOf(rules);
        }
    }

    record FormatResponse(String content) {}

    record ExecuteRequest(String content, String version, List<String> inputs) {
        ExecuteRequest {
            inputs = List.copyOf(inputs);
        }
    }
}
