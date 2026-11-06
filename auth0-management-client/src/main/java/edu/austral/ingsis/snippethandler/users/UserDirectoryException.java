package edu.austral.ingsis.snippethandler.users;

/** The identity provider could not be reached or refused the request. */
public class UserDirectoryException extends RuntimeException {

    public UserDirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
