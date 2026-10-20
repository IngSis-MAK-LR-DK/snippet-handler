package edu.austral.ingsis.snippethandler.language;

/** A language rule broken by a snippet, with the position where it happened. */
public record SyntaxError(String rule, String message, int line, int column) {}
