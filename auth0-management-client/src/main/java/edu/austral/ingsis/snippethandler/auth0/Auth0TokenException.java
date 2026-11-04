package edu.austral.ingsis.snippethandler.auth0;

/** Auth0 could not be reached or refused to issue a token. */
public class Auth0TokenException extends RuntimeException {

    public Auth0TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
