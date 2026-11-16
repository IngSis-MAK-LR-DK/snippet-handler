package edu.austral.ingsis.snippethandler.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private static final String INCOMING = "7f3a91c2-5d1e-4b7a-9c20-0a1b2c3d4e5f";
    private static final int TOO_LONG = 65;

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void keepsTheIdThatComesWithTheRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(
                requestWith(INCOMING),
                response,
                (req, res) -> duringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertEquals(INCOMING, duringRequest.get());
        assertEquals(INCOMING, response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void generatesAnIdWhenTheRequestHasNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (req, res) -> duringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));

        UUID.fromString(duringRequest.get());
        assertEquals(duringRequest.get(), response.getHeader(RequestIdFilter.HEADER));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "corto",
                "tiene espacios 12345",
                "salto\r\nde-linea-falso",
                "id/con/barras1"
            })
    void replacesAnIdThatIsNotSafeToLog(String incoming) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith(incoming), response, (req, res) -> {});

        String used = response.getHeader(RequestIdFilter.HEADER);
        assertNotEquals(incoming, used);
        UUID.fromString(used);
    }

    @Test
    void replacesAnIdThatIsTooLong() throws Exception {
        String incoming = "a".repeat(TOO_LONG);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith(incoming), response, (req, res) -> {});

        assertNotEquals(incoming, response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void clearsTheContextWhenTheRequestEnds() throws Exception {
        filter.doFilter(requestWith(INCOMING), new MockHttpServletResponse(), (req, res) -> {});

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void clearsTheContextEvenIfTheRequestFails() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        filter.doFilter(
                                requestWith(INCOMING),
                                new MockHttpServletResponse(),
                                (req, res) -> {
                                    throw new IllegalStateException("fallo");
                                }));

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    private static MockHttpServletRequest requestWith(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (requestId != null) {
            request.addHeader(RequestIdFilter.HEADER, requestId);
        }
        return request;
    }
}
