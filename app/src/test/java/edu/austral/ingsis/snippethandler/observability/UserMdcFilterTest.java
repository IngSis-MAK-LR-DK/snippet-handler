package edu.austral.ingsis.snippethandler.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserMdcFilterTest {

    private final UserMdcFilter filter = new UserMdcFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putsTheSubjectOfTheTokenInTheLogContext() throws Exception {
        authenticateWith(jwtOf("auth0|ana"));
        AtomicReference<String> duringRequest = new AtomicReference<>();

        run(duringRequest);

        assertEquals("auth0|ana", duringRequest.get());
        assertNull(MDC.get(UserMdcFilter.MDC_KEY));
    }

    @Test
    void leavesTheContextEmptyWithoutAuthentication() throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();

        run(duringRequest);

        assertNull(duringRequest.get());
    }

    @Test
    void ignoresAuthenticationsThatAreNotATokenFromAuth0() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("ana", "n/a"));
        AtomicReference<String> duringRequest = new AtomicReference<>();

        run(duringRequest);

        assertNull(duringRequest.get());
    }

    private void run(AtomicReference<String> duringRequest) throws Exception {
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, res) -> duringRequest.set(MDC.get(UserMdcFilter.MDC_KEY)));
    }

    private static void authenticateWith(Jwt jwt) {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Jwt jwtOf(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
    }
}
