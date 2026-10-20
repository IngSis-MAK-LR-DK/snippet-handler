package edu.austral.ingsis.snippethandler.rules;

/** A rule as configured for a user. */
public record RuleView(String name, String description, boolean enabled, String value) {}
