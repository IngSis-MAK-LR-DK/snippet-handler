package edu.austral.ingsis.snippethandler.language;

/** A lint rule broken by a snippet, with the position where it happened. */
public record Violation(String rule, String message, int line, int column) {}
