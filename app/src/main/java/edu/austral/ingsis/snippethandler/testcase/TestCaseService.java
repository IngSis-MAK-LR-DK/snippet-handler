package edu.austral.ingsis.snippethandler.testcase;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.language.ExecutionResult;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.SnippetRunner;
import edu.austral.ingsis.snippethandler.permission.Role;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetAccess;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tests of a snippet (US 8), running them (US 6/9) and re-running them on change (US 16). Every
 * operation is a single write, so none needs a transaction around its calls to other services.
 */
@Service
public class TestCaseService {

    private static final Logger LOG = LoggerFactory.getLogger(TestCaseService.class);

    private final TestCaseRepository repository;
    private final SnippetAccess access;
    private final SnippetContents contents;
    private final LanguageRegistry languages;

    public TestCaseService(
            TestCaseRepository repository,
            SnippetAccess access,
            SnippetContents contents,
            LanguageRegistry languages) {
        this.repository = repository;
        this.access = access;
        this.contents = contents;
        this.languages = languages;
    }

    public List<TestCaseView> list(UUID snippetId) {
        access.require(snippetId, Role.READER);
        return repository.findAllBySnippetIdOrderByCreatedAtAsc(snippetId).stream()
                .map(TestCaseView::of)
                .toList();
    }

    public TestCaseView create(UUID snippetId, TestCaseRequest request) {
        access.require(snippetId, Role.OWNER);
        TestCase test =
                new TestCase(
                        snippetId, request.name(), request.inputs(), request.expectedOutputs());
        return TestCaseView.of(repository.save(test));
    }

    public TestCaseView update(UUID snippetId, UUID testId, TestCaseRequest request) {
        access.require(snippetId, Role.OWNER);
        TestCase test = find(snippetId, testId);
        test.redefine(request.name(), request.inputs(), request.expectedOutputs());
        return TestCaseView.of(repository.save(test));
    }

    public void delete(UUID snippetId, UUID testId) {
        access.require(snippetId, Role.OWNER);
        repository.delete(find(snippetId, testId));
    }

    /** Anyone who can read the snippet can run its tests. */
    public TestRunResult run(UUID snippetId, UUID testId) {
        Snippet snippet = access.require(snippetId, Role.READER);
        return run(snippet, contents.read(snippetId), find(snippetId, testId));
    }

    /**
     * Runs a test that is not stored (the one being edited in the test panel). Nothing is saved;
     * the result has no test id.
     */
    public TestRunResult runUnsaved(UUID snippetId, TestRunRequest request) {
        Snippet snippet = access.require(snippetId, Role.READER);
        return evaluate(
                null,
                snippet,
                contents.read(snippetId),
                request.inputs(),
                request.expectedOutputs());
    }

    /** Runs every test of a snippet, recording each outcome. Internal: no permission check. */
    public List<TestRunResult> runAll(UUID snippetId) {
        Snippet snippet = access.find(snippetId);
        String content = contents.read(snippetId);
        return repository.findAllBySnippetIdOrderByCreatedAtAsc(snippetId).stream()
                .map(test -> run(snippet, content, test))
                .toList();
    }

    private TestRunResult run(Snippet snippet, String content, TestCase test) {
        TestRunResult result =
                evaluate(
                        test.getId(),
                        snippet,
                        content,
                        test.getInputs(),
                        test.getExpectedOutputs());
        test.ran(result.status());
        repository.save(test);
        return result;
    }

    /**
     * Executes the snippet with {@code inputs} and compares what it printed with {@code expected}.
     */
    private TestRunResult evaluate(
            UUID testId,
            Snippet snippet,
            String content,
            List<String> inputs,
            List<String> expected) {
        ExecutionResult result;
        TestStatus status;
        try {
            SnippetRunner runner = languages.get(snippet.getLanguage());
            result = runner.execute(content, snippet.getLanguageVersion(), inputs);
            status =
                    result.successful() && result.outputs().equals(expected)
                            ? TestStatus.PASSED
                            : TestStatus.FAILED;
        } catch (LanguageServiceException e) {
            LOG.warn("Could not run a test of snippet {}", snippet.getId(), e);
            result = new ExecutionResult(List.of(), List.of(e.getMessage()));
            status = TestStatus.ERROR;
        }
        return new TestRunResult(testId, status, result.outputs(), expected, result.errors());
    }

    private TestCase find(UUID snippetId, UUID testId) {
        return repository
                .findByIdAndSnippetId(testId, snippetId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Test %s of snippet %s not found"
                                                .formatted(testId, snippetId)));
    }
}
