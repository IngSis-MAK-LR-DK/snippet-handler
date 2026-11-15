package edu.austral.ingsis.snippethandler.security;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Authenticates every request as the handler itself, never as the caller. Unlike {@link
 * BearerTokenForwardingInterceptor}, which prefers the caller's token, this one ignores it: it is
 * for operations the receiving service accepts only from a trusted service.
 *
 * <p>Having no service credentials is a failure and not "send it without a token": a request that
 * must come from the handler would just be rejected, with a much less helpful error.
 */
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceTokenSource tokens;

    public ServiceTokenInterceptor(ServiceTokenSource tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().setBearerAuth(serviceToken());
        return execution.execute(request, body);
    }

    // Reported as an I/O failure so the client handles it like any other failed request.
    private String serviceToken() throws IOException {
        String token;
        try {
            token = tokens.token();
        } catch (RuntimeException e) {
            throw new IOException("Could not get the service token", e);
        }
        if (token == null) {
            throw new IOException("No service token: AUTH0_SERVICE_CLIENT_ID is not set");
        }
        return token;
    }
}
