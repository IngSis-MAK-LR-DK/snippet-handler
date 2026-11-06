package edu.austral.ingsis.snippethandler.security;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Forwards the caller's access token so downstream services know who is acting. Work that runs in
 * the background has no caller: it can fall back to a token of the handler itself.
 */
public class BearerTokenForwardingInterceptor implements ClientHttpRequestInterceptor {

    private final Supplier<String> serviceToken;

    /** Only the caller's token; requests made in the background go without one. */
    public BearerTokenForwardingInterceptor() {
        this(() -> null);
    }

    /**
     * @param serviceToken token used when there is no caller; it may return null to send none
     */
    public BearerTokenForwardingInterceptor(Supplier<String> serviceToken) {
        this.serviceToken = serviceToken;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String token = callerToken();
        if (token == null) {
            token = serviceToken();
        }
        if (token != null) {
            request.getHeaders().setBearerAuth(token);
        }
        return execution.execute(request, body);
    }

    private static String callerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getTokenValue()
                : null;
    }

    // Reported as an I/O failure so the client handles it like any other failed request.
    private String serviceToken() throws IOException {
        try {
            return serviceToken.get();
        } catch (RuntimeException e) {
            throw new IOException("Could not get the service token", e);
        }
    }
}
