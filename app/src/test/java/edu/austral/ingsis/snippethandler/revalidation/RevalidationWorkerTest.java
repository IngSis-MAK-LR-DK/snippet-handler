package edu.austral.ingsis.snippethandler.revalidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.snippet.Compliance;
import edu.austral.ingsis.snippethandler.snippet.LintReport;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.snippet.SnippetStyle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RevalidationWorkerTest {

    private static final String OWNER = "auth0|ana";

    private final RevalidationRunRepository runs = mock(RevalidationRunRepository.class);
    private final SnippetRepository snippets = mock(SnippetRepository.class);
    private final SnippetContents contents = mock(SnippetContents.class);
    private final SnippetStyle style = mock(SnippetStyle.class);
    private final LanguageRegistry languages = mock(LanguageRegistry.class);
    private final LanguageService service = mock(LanguageService.class);

    private RevalidationWorker worker;
    private RevalidationRun run;
    private UUID runId;

    @BeforeEach
    void setUp() {
        worker = new RevalidationWorker(runs, snippets, contents, style, languages);
        run = new RevalidationRun("printscript", "1.2", 2);
        runId = UUID.randomUUID();
        ReflectionTestUtils.setField(run, "id", runId);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(languages.get("printscript", "1.2")).thenReturn(service);
        when(service.language()).thenReturn("printscript");
    }

    @Test
    void validSnippetMovesToTheNewVersionAndGetsTheNewLintResult() {
        UUID id = UUID.randomUUID();
        Snippet snippet = snippet(id);
        when(snippets.findById(id)).thenReturn(Optional.of(snippet));
        when(contents.read(id)).thenReturn("let x = 1;");
        when(service.validate("let x = 1;", "1.2")).thenReturn(ValidationResult.ok());
        when(style.lint(OWNER, service, "1.2", "let x = 1;")).thenReturn(LintReport.of(List.of()));

        worker.revalidate(runId, List.of(id), "printscript", "1.2");

        assertEquals("1.2", snippet.getLanguageVersion());
        assertEquals(Compliance.COMPLIANT, snippet.getCompliance());
        verify(snippets).save(snippet);
    }

    @Test
    void invalidSnippetKeepsItsVersionAndIsMarkedFailed() {
        UUID id = UUID.randomUUID();
        Snippet snippet = snippet(id);
        when(snippets.findById(id)).thenReturn(Optional.of(snippet));
        when(contents.read(id)).thenReturn("let x = ;");
        when(service.validate("let x = ;", "1.2"))
                .thenReturn(new ValidationResult(false, List.of()));

        worker.revalidate(runId, List.of(id), "printscript", "1.2");

        assertEquals("1.1", snippet.getLanguageVersion());
        assertEquals(Compliance.FAILED, snippet.getCompliance());
    }

    @Test
    void aSnippetThatFailsIsCountedAndTheRunContinues() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(snippets.findById(broken)).thenReturn(Optional.of(snippet(broken)));
        when(contents.read(broken)).thenThrow(new IllegalStateException("bucket down"));
        Snippet snippet = snippet(fine);
        when(snippets.findById(fine)).thenReturn(Optional.of(snippet));
        when(contents.read(fine)).thenReturn("ok");
        when(service.validate("ok", "1.2")).thenReturn(ValidationResult.ok());
        when(style.lint(OWNER, service, "1.2", "ok")).thenReturn(LintReport.of(List.of()));

        worker.revalidate(runId, List.of(broken, fine), "printscript", "1.2");

        assertEquals(2, run.getProcessed());
        assertEquals(1, run.getFailed());
        assertEquals(RevalidationRun.RunStatus.COMPLETED, run.getStatus());
    }

    @Test
    void snippetDeletedWhileWaitingIsSkipped() {
        UUID id = UUID.randomUUID();
        when(snippets.findById(id)).thenReturn(Optional.empty());

        worker.revalidate(runId, List.of(id), "printscript", "1.2");

        assertEquals(1, run.getProcessed());
        assertEquals(0, run.getFailed());
    }

    private static Snippet snippet(UUID id) {
        Snippet snippet = new Snippet("name", null, "printscript", "1.1", OWNER);
        ReflectionTestUtils.setField(snippet, "id", id);
        return snippet;
    }
}
