package edu.austral.ingsis.snippethandler.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * The usual {@code Authorization: Bearer} header or, when a browser opens a WebSocket, the token
 * sent as subprotocol: {@code Sec-WebSocket-Protocol: bearer, <token>}. A browser cannot set
 * headers on a WebSocket, and a token in the URL would end up in the access logs.
 */
public class WebSocketBearerTokenResolver implements BearerTokenResolver {

    public static final String PROTOCOL = "bearer";
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private final DefaultBearerTokenResolver headers = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String token = headers.resolve(request);
        if (token != null) {
            return token;
        }
        String protocols = request.getHeader(PROTOCOL_HEADER);
        if (protocols == null || !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return null;
        }
        String[] values = protocols.split(",");
        for (int i = 0; i < values.length - 1; i++) {
            if (PROTOCOL.equals(values[i].trim())) {
                String candidate = values[i + 1].trim();
                return candidate.isEmpty() ? null : candidate;
            }
        }
        return null;
    }
}
