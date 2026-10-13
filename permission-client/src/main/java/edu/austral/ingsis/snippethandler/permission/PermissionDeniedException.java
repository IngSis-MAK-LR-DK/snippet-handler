package edu.austral.ingsis.snippethandler.permission;

/** The permissions service refused the operation to the current user. */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
