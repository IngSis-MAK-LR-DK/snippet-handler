package edu.austral.ingsis.snippethandler.execution;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.security.WebSocketBearerTokenResolver;
import edu.austral.ingsis.snippethandler.snippet.SnippetAccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * {@code /snippets/{id}/run}: streamed runs for the UI (US 9 and 10).
 *
 * <p>A browser cannot add an Authorization header to a WebSocket, so the token travels as a
 * subprotocol ({@code new WebSocket(url, ["bearer", token])}) and the server answers {@code
 * bearer}. See {@link WebSocketBearerTokenResolver}.
 */
@Configuration
@EnableWebSocket
public class ExecutionWebSocketConfig implements WebSocketConfigurer {

    private final SnippetExecutionService executions;
    private final SnippetAccess access;
    private final ObjectMapper json;
    private final SnippetProperties properties;

    public ExecutionWebSocketConfig(
            SnippetExecutionService executions,
            SnippetAccess access,
            ObjectMapper json,
            SnippetProperties properties) {
        this.executions = executions;
        this.access = access;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DefaultHandshakeHandler handshake = new DefaultHandshakeHandler();
        handshake.setSupportedProtocols(WebSocketBearerTokenResolver.PROTOCOL);
        String[] origins =
                properties.cors() == null
                        ? new String[0]
                        : properties.cors().allowedOrigins().toArray(String[]::new);
        registry.addHandler(new SnippetRunSocketHandler(executions, json), "/snippets/*/run")
                .setHandshakeHandler(handshake)
                .addInterceptors(new SnippetRunHandshake(access))
                .setAllowedOrigins(origins);
    }
}
