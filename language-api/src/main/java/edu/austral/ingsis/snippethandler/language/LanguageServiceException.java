package edu.austral.ingsis.snippethandler.language;

/** The language implementation could not complete the operation (adapter down, bad reply...). */
public class LanguageServiceException extends RuntimeException {

    public LanguageServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
