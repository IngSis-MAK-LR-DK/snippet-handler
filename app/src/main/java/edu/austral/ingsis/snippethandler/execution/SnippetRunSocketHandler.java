package edu.austral.ingsis.snippethandler.execution;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.austral.ingsis.snippethandler.snippet.Snippet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** {@code /snippets/{id}/run}: one {@link SnippetRun} per connection. */
public class SnippetRunSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetRunSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_LIMIT_BYTES = 512 * 1024;

    private final SnippetExecutionService executions;
    private final ObjectMapper json;
    private final Map<String, SnippetRun> runs = new ConcurrentHashMap<>();

    public SnippetRunSocketHandler(SnippetExecutionService executions, ObjectMapper json) {
        this.executions = executions;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Both the thread of the adapter stream and the one serving the session send messages:
        // the decorator serializes them.
        WebSocketSession safe =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
        Map<String, Object> attributes = session.getAttributes();
        runs.put(
                session.getId(),
                new SnippetRun(
                        (Snippet) attributes.get(SnippetRunHandshake.SNIPPET),
                        (String) attributes.get(SnippetRunHandshake.TOKEN),
                        executions,
                        sink(safe)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        JsonNode body;
        try {
            body = json.readTree(message.getPayload());
        } catch (JsonProcessingException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Not a JSON message"));
            return;
        }
        runs.get(session.getId()).handle(body);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SnippetRun run = runs.remove(session.getId());
        if (run != null) {
            run.cancel();
        }
    }

    private SnippetRun.MessageSink sink(WebSocketSession session) {
        return new SnippetRun.MessageSink() {
            @Override
            public void send(Map<String, Object> message) {
                if (!session.isOpen()) {
                    return;
                }
                try {
                    session.sendMessage(new TextMessage(json.writeValueAsString(message)));
                } catch (IOException e) {
                    LOG.debug("Could not send to the UI, it probably left", e);
                }
            }

            @Override
            public void close() {
                try {
                    session.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    LOG.debug("The session was already closed", e);
                }
            }
        };
    }
}
