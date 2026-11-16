package edu.austral.ingsis.snippethandler.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le pone un identificador a cada pedido y lo deja en el contexto de logs (MDC): todas las lineas
 * que se escriban mientras se atiende ese pedido lo llevan, sin que nadie lo pase a mano.
 *
 * <p>Si el pedido ya trae {@code X-Request-Id} (lo pone el proxy o el handler) se respeta, asi un
 * mismo pedido se sigue de punta a punta entre servicios. Se devuelve en la respuesta para poder
 * buscar en los logs lo que le paso a un pedido que fallo.
 *
 * <p>Corre antes que Spring Security: asi hasta un 401 queda con su identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /**
     * Lo que viene del cliente termina en los logs: solo se acepta un identificador "normal". Un
     * salto de linea en el header permitiria falsificar lineas de log.
     */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming != null && VALID_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
