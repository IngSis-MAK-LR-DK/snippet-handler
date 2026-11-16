package edu.austral.ingsis.snippethandler.language.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.SnippetStreamRunner;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.language.StreamRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Streamed runs against the adapter's {@code /execute/stream} WebSocket, with the WebSocket client
 * of the JDK ({@code java.net.http}): no extra library. The protocol is in
 * docs/language-adapter-contract.md.
 */
public class WebSocketStreamRunner implements SnippetStreamRunner {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final String language;
    private final URI streamUri;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param streamUri for example {@code ws://snippet-printscript:8080/execute/stream}
     */
    public WebSocketStreamRunner(String language, URI streamUri, HttpClient http) {
        this.language = language;
        this.streamUri = streamUri;
        this.http = http;
    }

    /** The stream endpoint of an adapter, from its HTTP base URL. */
    public static URI streamUriOf(String baseUrl) {
        String ws = baseUrl.replaceFirst("^http", "ws");
        return URI.create(ws.endsWith("/") ? ws + "execute/stream" : ws + "/execute/stream");
    }

    @Override
    public RunningSnippet stream(StreamRequest request, StreamListener listener) {
        Events events = new Events(listener);
        WebSocket.Builder builder = http.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT);
        if (request.bearerToken() != null) {
            builder.header("Authorization", "Bearer " + request.bearerToken());
        }
        WebSocket socket;
        try {
            socket =
                    builder.buildAsync(streamUri, events)
                            .get(CONNECT_TIMEOUT.toMillis() * 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("connect to", e);
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            throw failure("connect to", e);
        }
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("type", "start");
        start.put("content", request.source());
        start.put("version", request.version());
        start.put("inputs", request.inputs());
        start.put("interactive", request.interactive());
        send(socket, start);
        return new RunningSnippet() {
            @Override
            public void input(String value) {
                send(socket, Map.of("type", "input", "value", value == null ? "" : value));
            }

            @Override
            public void cancel() {
                socket.abort();
                events.finish(List.of("The run was cancelled"));
            }
        };
    }

    private void send(WebSocket socket, Map<String, Object> message) {
        try {
            socket.sendText(json.writeValueAsString(message), true).join();
        } catch (JsonProcessingException | CompletionException e) {
            throw failure("send a message to", e);
        }
    }

    private LanguageServiceException failure(String action, Exception cause) {
        return new LanguageServiceException(
                "Could not %s the %s adapter stream".formatted(action, language), cause);
    }

    /** Turns the adapter's messages into listener calls; {@code finished} happens only once. */
    private final class Events implements WebSocket.Listener {

        private final StreamListener listener;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final StringBuilder partial = new StringBuilder();

        Events(StreamListener listener) {
            this.listener = listener;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                handle(message);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            finish(
                    List.of(
                            "The %s adapter closed the stream before the end (%d %s)"
                                    .formatted(language, statusCode, reason)));
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            finish(List.of("The %s adapter stream failed: %s".formatted(language, error)));
        }

        private void handle(String message) {
            JsonNode body;
            try {
                body = json.readTree(message);
            } catch (JsonProcessingException e) {
                finish(List.of("The %s adapter sent an invalid message".formatted(language)));
                return;
            }
            switch (body.path("type").asText()) {
                case "output" -> listener.output(body.path("line").asText());
                case "input" -> listener.inputRequested(body.path("prompt").asText());
                case "done" -> {
                    List<String> errors = new ArrayList<>();
                    body.path("errors").forEach(error -> errors.add(error.asText()));
                    finish(errors);
                }
                default -> {
                    // Unknown messages are ignored: the adapter may add new ones.
                }
            }
        }

        void finish(List<String> errors) {
            if (finished.compareAndSet(false, true)) {
                listener.finished(errors);
            }
        }
    }
}
