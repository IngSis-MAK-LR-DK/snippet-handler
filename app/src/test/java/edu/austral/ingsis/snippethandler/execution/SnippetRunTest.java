package edu.austral.ingsis.snippethandler.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.language.StreamRequest;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.testcase.TestCase;
import edu.austral.ingsis.snippethandler.testcase.TestCaseService;
import edu.austral.ingsis.snippethandler.testcase.TestStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SnippetRunTest {

    private static final UUID SNIPPET_ID = UUID.randomUUID();
    private static final UUID TEST_ID = UUID.randomUUID();

    private final ObjectMapper json = new ObjectMapper();
    private final SnippetContents contents = mock(SnippetContents.class);
    private final LanguageService printScript = mock(LanguageService.class);
    private final TestCaseService tests = mock(TestCaseService.class);
    private final RunningSnippet running = mock(RunningSnippet.class);
    private final AtomicReference<StreamRequest> request = new AtomicReference<>();
    private final AtomicReference<StreamListener> adapter = new AtomicReference<>();
    private final Recorder ui = new Recorder();

    private SnippetRun run;

    @BeforeEach
    void setUp() {
        Snippet snippet = new Snippet("s", null, "printscript", "1.1", "auth0|ana");
        ReflectionTestUtils.setField(snippet, "id", SNIPPET_ID);
        when(printScript.language()).thenReturn("printscript");
        when(contents.read(SNIPPET_ID)).thenReturn("source");
        when(printScript.stream(any(), any()))
                .thenAnswer(
                        invocation -> {
                            request.set(invocation.getArgument(0));
                            adapter.set(invocation.getArgument(1));
                            return running;
                        });
        SnippetExecutionService executions =
                new SnippetExecutionService(
                        contents, new LanguageRegistry(List.of(printScript)), tests);
        run = new SnippetRun(snippet, "user-token", executions, ui);
    }

    @Test
    void anInteractiveRunForwardsOutputsInputsAndTheEnd() {
        run.handle(message("{\"type\":\"run\"}"));

        assertThat(request.get())
                .isEqualTo(new StreamRequest("source", "1.1", List.of(), true, "user-token"));
        adapter.get().output("hola");
        adapter.get().inputRequested("nombre");
        run.handle(message("{\"type\":\"input\",\"value\":\"Ana\"}"));
        adapter.get().finished(List.of());

        verify(running).input("Ana");
        assertThat(ui.messages)
                .containsExactly(
                        Map.of("type", "output", "line", "hola"),
                        Map.of("type", "input", "prompt", "nombre"),
                        Map.of("type", "done", "errors", List.of()));
        assertThat(ui.closed).isTrue();
    }

    @Test
    void aSavedTestStreamsItsOutputAndRecordsTheOutcome() {
        TestCase saved = new TestCase(SNIPPET_ID, "t", List.of("Ana"), List.of("hola Ana"));
        ReflectionTestUtils.setField(saved, "id", TEST_ID);
        when(tests.test(SNIPPET_ID, TEST_ID)).thenReturn(saved);

        run.handle(message("{\"type\":\"test\",\"testId\":\"" + TEST_ID + "\"}"));
        assertThat(request.get().inputs()).containsExactly("Ana");
        assertThat(request.get().interactive()).isFalse();
        adapter.get().output("hola Ana");
        adapter.get().finished(List.of());

        verify(tests).recordRun(SNIPPET_ID, TEST_ID, TestStatus.PASSED);
        assertThat(ui.messages.get(0)).isEqualTo(Map.of("type", "output", "line", "hola Ana"));
        assertThat(ui.messages.get(1))
                .containsEntry("type", "result")
                .containsEntry("testId", TEST_ID)
                .containsEntry("status", "PASSED")
                .containsEntry("outputs", List.of("hola Ana"));
        assertThat(ui.closed).isTrue();
    }

    @Test
    void aDraftTestIsNotRecorded() {
        run.handle(
                message("{\"type\":\"test\",\"inputs\":[\"x\"]," + "\"expectedOutputs\":[\"y\"]}"));
        adapter.get().output("x");
        adapter.get().finished(List.of());

        verify(tests, never()).recordRun(any(), any(), any());
        assertThat(ui.messages.get(1))
                .containsEntry("status", "FAILED")
                .containsEntry("expectedOutputs", List.of("y"));
    }

    @Test
    void aTestOfAnotherSnippetIsRefused() {
        when(tests.test(SNIPPET_ID, TEST_ID)).thenThrow(new NotFoundException("no such test"));

        run.handle(message("{\"type\":\"test\",\"testId\":\"" + TEST_ID + "\"}"));

        assertThat(ui.messages).containsExactly(Map.of("type", "error", "message", "no such test"));
        assertThat(ui.closed).isTrue();
    }

    @Test
    void aTestWhoseAdapterIsDownEndsInError() {
        when(printScript.stream(any(), any()))
                .thenThrow(new LanguageServiceException("down", null));

        run.handle(message("{\"type\":\"test\",\"inputs\":[],\"expectedOutputs\":[]}"));

        assertThat(ui.messages.get(0))
                .containsEntry("status", "ERROR")
                .containsEntry("errors", List.of("down"));
    }

    @Test
    void anInteractiveRunWhoseAdapterIsDownEndsWithTheError() {
        when(printScript.stream(any(), any()))
                .thenThrow(new LanguageServiceException("down", null));

        run.handle(message("{\"type\":\"run\"}"));

        assertThat(ui.messages).containsExactly(Map.of("type", "done", "errors", List.of("down")));
    }

    @Test
    void onlyOneRunAtATime() {
        run.handle(message("{\"type\":\"run\"}"));

        run.handle(message("{\"type\":\"run\"}"));

        assertThat(ui.messages.get(0)).containsEntry("type", "error");
    }

    @Test
    void anUnknownMessageIsRefused() {
        run.handle(message("{\"type\":\"dance\"}"));

        assertThat(ui.messages.get(0)).containsEntry("type", "error");
    }

    @Test
    void anInputBeforeStartingIsIgnored() {
        run.handle(message("{\"type\":\"input\",\"value\":\"x\"}"));

        assertThat(ui.messages).isEmpty();
    }

    @Test
    void whenTheUiLeavesTheRunIsCancelled() {
        run.cancel();
        run.handle(message("{\"type\":\"run\"}"));

        run.cancel();

        verify(running).cancel();
    }

    private JsonNode message(String text) {
        try {
            return json.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static final class Recorder implements SnippetRun.MessageSink {
        private final List<Map<String, Object>> messages = new ArrayList<>();
        private boolean closed;

        @Override
        public void send(Map<String, Object> message) {
            messages.add(message);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
