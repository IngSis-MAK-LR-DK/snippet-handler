package edu.austral.ingsis.snippethandler.language;

public interface SnippetValidator {

    /** Parses the source and reports every syntax / semantic rule it breaks. */
    ValidationResult validate(String source, String version);
}
