package edu.austral.ingsis.snippethandler.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.snippet.Compliance;
import edu.austral.ingsis.snippethandler.snippet.LintReport;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.snippet.SnippetStore;
import edu.austral.ingsis.snippethandler.snippet.SnippetStyle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SnippetReprocessorTest {

    private static final String OWNER = "auth0|ana";

    private final ReprocessTaskRepository tasks = mock(ReprocessTaskRepository.class);
    private final ReprocessJobRepository jobs = mock(ReprocessJobRepository.class);
    private final SnippetRepository snippets = mock(SnippetRepository.class);
    private final SnippetContents contents = mock(SnippetContents.class);
    private final SnippetStore store = mock(SnippetStore.class);
    private final SnippetStyle style = mock(SnippetStyle.class);
    private final LanguageRegistry languages = mock(LanguageRegistry.class);
    private final LanguageService service = mock(LanguageService.class);

    private SnippetReprocessor reprocessor;
    private UUID snippetId;
    private Snippet snippet;
    private ReprocessTask task;

    @BeforeEach
    void setUp() {
        reprocessor =
                new SnippetReprocessor(
                        tasks,
                        jobs,
                        snippets,
                        contents,
                        store,
                        style,
                        languages,
                        new ReprocessingProperties(true, 2, null, null));
        snippetId = UUID.randomUUID();
        snippet = new Snippet("s", "d", "printscript", "1.0", OWNER);
        ReflectionTestUtils.setField(snippet, "id", snippetId);
        when(snippets.findById(snippetId)).thenReturn(Optional.of(snippet));
        when(languages.get("printscript")).thenReturn(service);
        when(contents.read(snippetId)).thenReturn("let x=1;");
    }

    @Test
    void lintStoresTheNewReportAndFinishesTheTask() {
        givenTask(ReprocessType.LINT);
        LintReport report = LintReport.of(List.of());
        when(style.lintStrict(OWNER, service, "1.0", "let x=1;")).thenReturn(report);

        reprocessor.handle(message());

        assertThat(snippet.getCompliance()).isEqualTo(Compliance.COMPLIANT);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getAttempts()).isEqualTo(1);
        verify(snippets).save(snippet);
    }

    @Test
    void formatSavesTheSnippetOnlyWhenTheSourceChanges() {
        givenTask(ReprocessType.FORMAT);
        when(style.format(OWNER, service, "1.0", "let x=1;")).thenReturn("let x = 1;");
        when(style.lint(OWNER, service, "1.0", "let x = 1;")).thenReturn(LintReport.of(List.of()));

        reprocessor.handle(message());

        verify(store)
                .save(
                        any(),
                        org.mockito.ArgumentMatchers.eq("let x = 1;"),
                        org.mockito.ArgumentMatchers.eq("let x=1;"),
                        any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void formatLeavesAnAlreadyFormattedSnippetAlone() {
        givenTask(ReprocessType.FORMAT);
        when(style.format(OWNER, service, "1.0", "let x=1;")).thenReturn("let x=1;");

        reprocessor.handle(message());

        verify(store, never()).save(any(), any(), any(), any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void aFailureWithTriesLeftIsRethrownSoTheMessageComesBack() {
        givenTask(ReprocessType.LINT);
        when(style.lintStrict(any(), any(), any(), any()))
                .thenThrow(new LanguageServiceException("adapter down", null));

        assertThatThrownBy(() -> reprocessor.handle(message()))
                .isInstanceOf(LanguageServiceException.class);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getError()).isEqualTo("adapter down");
    }

    @Test
    void aSnippetThatKeepsFailingIsSkippedAndMarkedAsFailed() {
        givenTask(ReprocessType.LINT);
        when(style.lintStrict(any(), any(), any(), any()))
                .thenThrow(new LanguageServiceException("adapter down", null));
        assertThatThrownBy(() -> reprocessor.handle(message()))
                .isInstanceOf(LanguageServiceException.class);

        reprocessor.handle(message()); // second and last attempt: no exception, the run goes on

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(snippet.getCompliance()).isEqualTo(Compliance.FAILED);
    }

    @Test
    void attemptsThatNeverReportedBackCountAgainstTheSnippet() {
        givenTask(ReprocessType.LINT);
        task.attempt();
        task.attempt(); // the process died during both

        reprocessor.handle(message());

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        verify(style, never()).lintStrict(any(), any(), any(), any());
    }

    @Test
    void aFinishedTaskIsNotProcessedAgain() {
        givenTask(ReprocessType.LINT);
        task.done();

        reprocessor.handle(message());

        verify(style, never()).lintStrict(any(), any(), any(), any());
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void aDeletedSnippetCountsAsDone() {
        givenTask(ReprocessType.LINT);
        when(snippets.findById(snippetId)).thenReturn(Optional.empty());

        reprocessor.handle(message());

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void revalidationMovesAValidSnippetToTheNewVersionAndLintsIt() {
        givenTask(ReprocessType.REVALIDATE);
        when(languages.get("printscript", "1.2")).thenReturn(service);
        when(service.language()).thenReturn("printscript");
        when(service.validate("let x=1;", "1.2")).thenReturn(ValidationResult.ok());
        when(style.lintStrict(OWNER, service, "1.2", "let x=1;"))
                .thenReturn(LintReport.of(List.of()));

        reprocessor.handle(message());

        assertThat(snippet.getLanguageVersion()).isEqualTo("1.2");
        assertThat(snippet.getCompliance()).isEqualTo(Compliance.COMPLIANT);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void revalidationKeepsTheVersionOfAnInvalidSnippetAndMarksItFailed() {
        givenTask(ReprocessType.REVALIDATE);
        when(languages.get("printscript", "1.2")).thenReturn(service);
        when(service.validate("let x=1;", "1.2"))
                .thenReturn(new ValidationResult(false, List.of()));

        reprocessor.handle(message());

        assertThat(snippet.getLanguageVersion()).isEqualTo("1.0");
        assertThat(snippet.getCompliance()).isEqualTo(Compliance.FAILED);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void aRevalidationThatKeepsFailingLeavesTheSnippetAsItWas() {
        givenTask(ReprocessType.REVALIDATE);
        when(languages.get("printscript", "1.2")).thenReturn(service);
        when(service.validate(any(), any())).thenThrow(new LanguageServiceException("down", null));
        assertThatThrownBy(() -> reprocessor.handle(message()))
                .isInstanceOf(LanguageServiceException.class);

        reprocessor.handle(message());

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(snippet.getLanguageVersion()).isEqualTo("1.0");
        assertThat(snippet.getCompliance()).isNotEqualTo(Compliance.FAILED);
    }

    @Test
    void anUnknownTaskIsIgnored() {
        when(tasks.findById(any())).thenReturn(Optional.empty());

        reprocessor.handle(Map.of("taskId", UUID.randomUUID().toString()));

        verify(tasks, never()).saveAndFlush(any());
    }

    private void givenTask(ReprocessType type) {
        UUID jobId = UUID.randomUUID();
        task = new ReprocessTask(jobId, snippetId);
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
        when(jobs.findById(jobId))
                .thenReturn(
                        Optional.of(
                                new ReprocessJob(
                                        jobId,
                                        OWNER,
                                        type,
                                        "printscript",
                                        type == ReprocessType.REVALIDATE ? "1.2" : null)));
    }

    private Map<String, String> message() {
        return Map.of("taskId", task.getId().toString());
    }
}
