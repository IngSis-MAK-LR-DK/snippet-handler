package edu.austral.ingsis.snippethandler.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.language.StreamRequest;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.testcase.TestCase;
import edu.austral.ingsis.snippethandler.testcase.TestCaseService;
import edu.austral.ingsis.snippethandler.testcase.TestRunResult;
import edu.austral.ingsis.snippethandler.testcase.TestStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Streamed runs of a snippet: interactive (US 10) and of one test (US 9). Who may run it is checked
 * before (when the WebSocket opens); here the snippet is already readable by the caller.
 */
@Service
public class SnippetExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetExecutionService.class);

    private final SnippetContents contents;
    private final LanguageRegistry languages;
    private final TestCaseService tests;

    public SnippetExecutionService(
            SnippetContents contents, LanguageRegistry languages, TestCaseService tests) {
        this.contents = contents;
        this.languages = languages;
        this.tests = tests;
    }

    /** US 10: the inputs are asked for as the snippet needs them. */
    public RunningSnippet interactive(Snippet snippet, String token, StreamListener listener) {
        return start(snippet, token, List.of(), true, listener);
    }

    /** What a test needs to run; {@code testId} is null for a draft that is not saved. */
    public record TestDefinition(UUID testId, List<String> inputs, List<String> expectedOutputs) {
        public TestDefinition {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            expectedOutputs = expectedOutputs == null ? List.of() : List.copyOf(expectedOutputs);
        }

        static TestDefinition of(TestCase test) {
            return new TestDefinition(test.getId(), test.getInputs(), test.getExpectedOutputs());
        }
    }

    /** Events of a test run: each printed line as it happens, then the outcome. */
    public interface TestListener {
        void output(String line);

        void result(TestRunResult result);
    }

    /** The saved test {@code testId} of the snippet. */
    public TestDefinition savedTest(Snippet snippet, UUID testId) {
        return TestDefinition.of(tests.test(snippet.getId(), testId));
    }

    /**
     * US 9: runs one test streaming its output; the outcome of a saved test is recorded like the
     * one of a non-streamed run.
     */
    public RunningSnippet test(
            Snippet snippet, String token, TestDefinition test, TestListener listener) {
        List<String> outputs = Collections.synchronizedList(new ArrayList<>());
        StreamListener events =
                new StreamListener() {
                    @Override
                    public void output(String line) {
                        outputs.add(line);
                        listener.output(line);
                    }

                    @Override
                    public void inputRequested(String prompt) {
                        // Not interactive: the adapter fails the run instead of asking.
                    }

                    @Override
                    public void finished(List<String> errors) {
                        List<String> printed = List.copyOf(outputs);
                        TestStatus status =
                                TestCaseService.statusOf(printed, errors, test.expectedOutputs());
                        listener.result(result(snippet, test, status, printed, errors));
                    }
                };
        try {
            return start(snippet, token, test.inputs(), false, events);
        } catch (LanguageServiceException | UnsupportedLanguageException e) {
            LOG.warn("Could not run test {} of snippet {}", test.testId(), snippet.getId(), e);
            listener.result(
                    result(snippet, test, TestStatus.ERROR, List.of(), List.of(e.getMessage())));
            return NOTHING_RUNNING;
        }
    }

    private TestRunResult result(
            Snippet snippet,
            TestDefinition test,
            TestStatus status,
            List<String> outputs,
            List<String> errors) {
        if (test.testId() != null) {
            tests.recordRun(snippet.getId(), test.testId(), status);
        }
        return new TestRunResult(test.testId(), status, outputs, test.expectedOutputs(), errors);
    }

    private RunningSnippet start(
            Snippet snippet,
            String token,
            List<String> inputs,
            boolean interactive,
            StreamListener listener) {
        String source = contents.read(snippet.getId());
        return languages.get(snippet.getLanguage()).stream(
                new StreamRequest(source, snippet.getLanguageVersion(), inputs, interactive, token),
                listener);
    }

    /** For a run that ended before starting: there is nothing to answer or cancel. */
    static final RunningSnippet NOTHING_RUNNING =
            new RunningSnippet() {
                @Override
                public void input(String value) {}

                @Override
                public void cancel() {}
            };
}
