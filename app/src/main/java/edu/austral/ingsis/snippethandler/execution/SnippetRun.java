package edu.austral.ingsis.snippethandler.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.execution.SnippetExecutionService.TestDefinition;
import edu.austral.ingsis.snippethandler.execution.SnippetExecutionService.TestListener;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.testcase.TestRunResult;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One WebSocket connection of the UI: a single run, interactive or of a test, and then it closes.
 * The protocol is in docs/streaming-execution.md.
 */
public class SnippetRun {

    /** Where the messages for the UI go: the WebSocket, or a list in the tests. */
    public interface MessageSink {
        void send(Map<String, Object> message);

        void close();
    }

    private final Snippet snippet;
    private final String token;
    private final SnippetExecutionService executions;
    private final MessageSink sink;
    private RunningSnippet running;

    public SnippetRun(
            Snippet snippet, String token, SnippetExecutionService executions, MessageSink sink) {
        this.snippet = snippet;
        this.token = token;
        this.executions = executions;
        this.sink = sink;
    }

    /** A message from the UI. */
    public synchronized void handle(JsonNode message) {
        String type = message.path("type").asText();
        if ("input".equals(type)) {
            if (running != null) {
                running.input(message.path("value").asText());
            }
            return;
        }
        if (running != null) {
            fail("A run is already in progress: only one at a time");
            return;
        }
        switch (type) {
            case "run" -> running = startInteractive();
            case "test" -> running = startTest(message);
            default -> fail("Unknown message type '%s'".formatted(type));
        }
    }

    /** The UI left: nobody is waiting for the run. */
    public synchronized void cancel() {
        if (running != null) {
            running.cancel();
        }
    }

    private RunningSnippet startInteractive() {
        try {
            return executions.interactive(
                    snippet,
                    token,
                    new StreamListener() {
                        @Override
                        public void output(String line) {
                            sink.send(Map.of("type", "output", "line", line));
                        }

                        @Override
                        public void inputRequested(String prompt) {
                            sink.send(Map.of("type", "input", "prompt", prompt));
                        }

                        @Override
                        public void finished(List<String> errors) {
                            sink.send(Map.of("type", "done", "errors", errors));
                            sink.close();
                        }
                    });
        } catch (LanguageServiceException | UnsupportedLanguageException e) {
            sink.send(Map.of("type", "done", "errors", List.of(e.getMessage())));
            sink.close();
            return SnippetExecutionService.NOTHING_RUNNING;
        }
    }

    private RunningSnippet startTest(JsonNode message) {
        TestDefinition test;
        try {
            test = definition(message);
        } catch (NotFoundException | IllegalArgumentException e) {
            fail(e.getMessage());
            return null;
        }
        return executions.test(
                snippet,
                token,
                test,
                new TestListener() {
                    @Override
                    public void output(String line) {
                        sink.send(Map.of("type", "output", "line", line));
                    }

                    @Override
                    public void result(TestRunResult result) {
                        Map<String, Object> message = new LinkedHashMap<>();
                        message.put("type", "result");
                        message.put("testId", result.testId());
                        message.put("status", result.status().name());
                        message.put("outputs", result.outputs());
                        message.put("expectedOutputs", result.expectedOutputs());
                        message.put("errors", result.errors());
                        sink.send(message);
                        sink.close();
                    }
                });
    }

    /** A saved test ({@code testId}) or a draft ({@code inputs} and {@code expectedOutputs}). */
    private TestDefinition definition(JsonNode message) {
        JsonNode testId = message.path("testId");
        if (testId.isTextual()) {
            return executions.savedTest(snippet, UUID.fromString(testId.asText()));
        }
        return new TestDefinition(
                null, strings(message.path("inputs")), strings(message.path("expectedOutputs")));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void fail(String reason) {
        sink.send(Map.of("type", "error", "message", reason));
        sink.close();
    }
}
