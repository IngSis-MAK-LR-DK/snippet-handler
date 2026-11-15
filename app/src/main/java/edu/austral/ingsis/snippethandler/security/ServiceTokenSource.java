package edu.austral.ingsis.snippethandler.security;

/**
 * The handler's own access token for the platform's API, obtained from Auth0 with client
 * credentials (machine to machine). It is what the handler uses when it acts as itself and not on
 * behalf of a user: background work and vouching for who owns a new snippet.
 *
 * <p>There is a single source for the whole handler: Auth0 limits how many machine tokens it
 * issues, and the token is reused until shortly before it expires.
 */
@FunctionalInterface
public interface ServiceTokenSource {

    /** A valid token, or {@code null} when no service credentials are configured. */
    String token();
}
