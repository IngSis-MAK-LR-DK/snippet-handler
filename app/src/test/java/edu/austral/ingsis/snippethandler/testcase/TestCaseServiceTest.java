package edu.austral.ingsis.snippethandler.testcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.ExecutionResult;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetAccess;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TestCaseServiceTest {

    private static final UUID SNIPPET = UUID.randomUUID();

    private final TestCaseRepository repository = mock(TestCaseRepository.class);
    private final SnippetAccess access = mock(SnippetAccess.class);
    private final SnippetContents contents = mock(SnippetContents.class);
    private final LanguageService printScript = mock(LanguageService.class);

    private TestCaseService service;

    @BeforeEach
    void setUp() {
        when(printScript.language()).thenReturn("printscript");
        when(access.find(SNIPPET))
                .thenReturn(new Snippet("s", null, "printscript", "1.0", "auth0|alice"));
        when(contents.read(SNIPPET)).thenReturn("println(readInput());");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service =
                new TestCaseService(
                        repository, access, contents, new LanguageRegistry(List.of(printScript)));
    }

    @Test
    void runAllRecordsEachOutcome() {
        TestCase passing = test(List.of("a"), List.of("a"));
        TestCase failing = test(List.of("b"), List.of("not b"));
        TestCase erroring = test(List.of("c"), List.of("c"));
        when(repository.findAllBySnippetIdOrderByCreatedAtAsc(SNIPPET))
                .thenReturn(List.of(passing, failing, erroring));
        when(printScript.execute(anyString(), anyString(), anyList()))
                .thenAnswer(
                        invocation -> {
                            List<String> inputs = invocation.getArgument(2);
                            if (inputs.equals(List.of("c"))) {
                                throw new LanguageServiceException("down", null);
                            }
                            return new ExecutionResult(inputs, List.of());
                        });

        List<TestRunResult> results = service.runAll(SNIPPET);

        assertThat(results)
                .extracting(TestRunResult::status)
                .containsExactly(TestStatus.PASSED, TestStatus.FAILED, TestStatus.ERROR);
        assertThat(erroring.getLastStatus()).isEqualTo(TestStatus.ERROR);
        assertThat(passing.getLastRunAt()).isNotNull();
    }

    @Test
    void runtimeErrorsFailTheTest() {
        TestCase test = test(List.of(), List.of());
        when(repository.findAllBySnippetIdOrderByCreatedAtAsc(SNIPPET)).thenReturn(List.of(test));
        when(printScript.execute(anyString(), anyString(), anyList()))
                .thenReturn(new ExecutionResult(List.of(), List.of("division by zero")));

        assertThat(service.runAll(SNIPPET).get(0).status()).isEqualTo(TestStatus.FAILED);
    }

    private static TestCase test(List<String> inputs, List<String> outputs) {
        TestCase test = new TestCase(SNIPPET, "t", inputs, outputs);
        ReflectionTestUtils.setField(test, "id", UUID.randomUUID());
        return test;
    }
}
