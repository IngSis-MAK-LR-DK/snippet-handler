package edu.austral.ingsis.snippethandler.language;

import java.util.List;
import java.util.Set;

/**
 * Contract between the snippet handler and a programming language. Each supported language
 * (PrintScript today) provides an implementation, usually backed by its own adapter service.
 */
public interface LanguageService {

    /** Language identifier, e.g. {@code printscript}. */
    String language();

    /** Versions of the language this implementation understands. */
    Set<String> versions();

    /** File extension (without dot) used when downloading snippets of this language. */
    String fileExtension();

    /** Parses the source and reports every syntax / semantic rule it breaks. */
    ValidationResult validate(String source, String version);

    /** Evaluates the source against the given lint rules. */
    List<Violation> lint(String source, String version, List<RuleConfig> rules);

    /** Returns the source formatted according to the given format rules. */
    String format(String source, String version, List<RuleConfig> rules);

    /** Runs the source feeding {@code inputs} in order and collecting every printed line. */
    ExecutionResult execute(String source, String version, List<String> inputs);

    /** Rules the language supports out of the box, with their default configuration. */
    List<RuleDefinition> defaultRules(RuleType type);
}
