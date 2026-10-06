package edu.austral.ingsis.snippethandler.error;

import java.util.List;

import edu.austral.ingsis.snippethandler.language.SyntaxError;

/** The snippet breaks rules of its language; carries each rule with its line and column. */
public class InvalidSnippetException extends RuntimeException {

    private final List<SyntaxError> errors;

    public InvalidSnippetException(List<SyntaxError> errors) {
        super("Snippet is not valid according to its language rules");
        this.errors = List.copyOf(errors);
    }

    public List<SyntaxError> getErrors() {
        return errors;
    }
}
