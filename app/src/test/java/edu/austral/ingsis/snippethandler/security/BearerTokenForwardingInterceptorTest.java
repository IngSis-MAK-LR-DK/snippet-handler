package edu.austral.ingsis.snippethandler.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

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

class BearerTokenForwardingInterceptorTest {

    private static final ClientHttpRequestExecution OK =
            (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    private final MockClientHttpRequest request =
            new MockClientHttpRequest(HttpMethod.POST, URI.create("http://adapter/validate"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsTheCallersTokenEvenWithAServiceToken() throws IOException {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                new Jwt(
                                        "user-token",
                                        Instant.now(),
                                        Instant.now().plusSeconds(60),
                                        Map.of("alg", "none"),
                                        Map.of("sub", "auth0|alice"))));

        new BearerTokenForwardingInterceptor(() -> "service-token")
                .intercept(request, new byte[0], OK);

        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer user-token");
    }

    @Test
    void usesTheServiceTokenWhenThereIsNoCaller() throws IOException {
        new BearerTokenForwardingInterceptor(() -> "service-token")
                .intercept(request, new byte[0], OK);

        assertThat(request.getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer service-token");
    }

    @Test
    void sendsNoTokenWithoutCallerNorServiceToken() throws IOException {
        new BearerTokenForwardingInterceptor().intercept(request, new byte[0], OK);

        assertThat(request.getHeaders().containsKey("Authorization")).isFalse();
    }

    @Test
    void serviceTokenFailureIsAnIoFailure() {
        BearerTokenForwardingInterceptor interceptor =
                new BearerTokenForwardingInterceptor(
                        () -> {
                            throw new IllegalStateException("Auth0 down");
                        });

        assertThrows(IOException.class, () -> interceptor.intercept(request, new byte[0], OK));
    }
}
