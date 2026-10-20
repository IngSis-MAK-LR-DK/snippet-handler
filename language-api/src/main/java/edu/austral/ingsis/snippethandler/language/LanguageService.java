package edu.austral.ingsis.snippethandler.language;

import java.util.Set;

/**
 * Everything the snippet handler needs from a programming language. Each supported language
 * (PrintScript today) provides an implementation, usually backed by its own adapter service.
 *
 * <p>Each capability is its own interface so collaborators depend only on what they use (running
 * tests only needs a {@link SnippetRunner}).
 */
public interface LanguageService
        extends SnippetValidator, SnippetLinter, SnippetFormatter, SnippetRunner, RuleCatalog {

    /** Language identifier, e.g. {@code printscript}. */
    String language();

    /** Versions of the language this implementation understands. */
    Set<String> versions();

    /** File extension (without dot) used when downloading snippets of this language. */
    String fileExtension();
}
