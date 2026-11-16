package edu.austral.ingsis.snippethandler.observability;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Le pasa el {@code X-Request-Id} del pedido actual a los servicios que el handler llama (Permisos,
 * los adaptadores, assets). Ellos lo respetan, asi un mismo pedido se busca con un solo id en los
 * logs de todos. Fuera de un pedido (trabajo en segundo plano sin id) no agrega nada.
 */
public class RequestIdForwarding implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null && !request.getHeaders().containsKey(RequestIdFilter.HEADER)) {
            request.getHeaders().set(RequestIdFilter.HEADER, requestId);
        }
        return execution.execute(request, body);
    }
}
