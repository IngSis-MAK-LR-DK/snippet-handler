package edu.austral.ingsis.snippethandler.language;

/**
 * Configuration of a single rule. {@code value} is optional and rule specific (e.g. the number of
 * spaces for an indentation rule).
 */
public record RuleConfig(String name, boolean enabled, String value) {}
