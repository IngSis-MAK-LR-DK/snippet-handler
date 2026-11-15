package edu.austral.ingsis.snippethandler.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ServiceTokenInterceptorTest {

    private static final ClientHttpRequestExecution OK =
            (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    private final MockClientHttpRequest request =
            new MockClientHttpRequest(
                    HttpMethod.POST, URI.create("http://permissions/permissions"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAsTheHandler() throws IOException {
        new ServiceTokenInterceptor(() -> "service-token").intercept(request, new byte[0], OK);

        assertThat(request.getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer service-token");
    }

    @Test
    void ignoresTheCallersToken() throws IOException {
        // A user token would be refused by the permissions service for this operation.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                new Jwt(
                                        "user-token",
                                        Instant.now(),
                                        Instant.now().plusSeconds(60),
                                        Map.of("alg", "none"),
                                        Map.of("sub", "auth0|alice"))));

        new ServiceTokenInterceptor(() -> "service-token").intercept(request, new byte[0], OK);

        assertThat(request.getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer service-token");
    }

    @Test
    void withoutServiceCredentialsItFailsInsteadOfSendingTheRequestUnauthenticated()
            throws IOException {
        AtomicBoolean sent = new AtomicBoolean();
        ClientHttpRequestExecution recording =
                (req, body) -> {
                    sent.set(true);
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                };
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(() -> null);

        assertThrows(
                IOException.class, () -> interceptor.intercept(request, new byte[0], recording));

        assertThat(sent).isFalse();
    }

    @Test
    void serviceTokenFailureIsAnIoFailure() {
        ServiceTokenInterceptor interceptor =
                new ServiceTokenInterceptor(
                        () -> {
                            throw new IllegalStateException("Auth0 down");
                        });

        assertThrows(IOException.class, () -> interceptor.intercept(request, new byte[0], OK));
    }
}
