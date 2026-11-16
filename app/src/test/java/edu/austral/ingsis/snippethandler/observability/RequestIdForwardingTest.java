package edu.austral.ingsis.snippethandler.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RequestIdForwardingTest {

    private final RequestIdForwarding forwarding = new RequestIdForwarding();
    private final AtomicReference<String> sent = new AtomicReference<>();
    private final ClientHttpRequestExecution execution =
            (request, body) -> {
                sent.set(request.getHeaders().getFirst(RequestIdFilter.HEADER));
                return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            };

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void theCalledServiceGetsTheIdOfTheCurrentRequest() throws IOException {
        MDC.put(RequestIdFilter.MDC_KEY, "7f3a91c2-5d1e");

        forwarding.intercept(request(), new byte[0], execution);

        assertThat(sent.get()).isEqualTo("7f3a91c2-5d1e");
    }

    @Test
    void withoutARequestNoIdIsSent() throws IOException {
        forwarding.intercept(request(), new byte[0], execution);

        assertThat(sent.get()).isNull();
    }

    @Test
    void anIdAlreadySetIsKept() throws IOException {
        MDC.put(RequestIdFilter.MDC_KEY, "7f3a91c2-5d1e");
        HttpRequest request = request();
        request.getHeaders().set(RequestIdFilter.HEADER, "otro-id-12345");

        forwarding.intercept(request, new byte[0], execution);

        assertThat(sent.get()).isEqualTo("otro-id-12345");
    }

    private static HttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create("http://permission-service/x"));
    }
}
