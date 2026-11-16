package edu.austral.ingsis.snippethandler.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserContextFilterTest {

    private final UserContextFilter filter = new UserContextFilter();
    private final AtomicReference<String> userWhileServing = new AtomicReference<>();
    private final FilterChain chain =
            (request, response) -> userWhileServing.set(MDC.get(UserContextFilter.USER));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void theLinesOfARequestCarryTheSubjectOfItsToken() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                new Jwt(
                                        "user-token",
                                        Instant.now(),
                                        Instant.now().plusSeconds(60),
                                        Map.of("alg", "none"),
                                        Map.of("sub", "auth0|ana"))));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(userWhileServing.get()).isEqualTo("auth0|ana");
    }

    @Test
    void theUserDoesNotLeakIntoTheNextRequestOfTheSameThread() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                new Jwt(
                                        "user-token",
                                        Instant.now(),
                                        Instant.now().plusSeconds(60),
                                        Map.of("alg", "none"),
                                        Map.of("sub", "auth0|ana"))));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(MDC.get(UserContextFilter.USER)).isNull();
    }

    @Test
    void anUnauthenticatedRequestHasNoUser() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(userWhileServing.get()).isNull();
    }
}
