package edu.austral.ingsis.snippethandler.revalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJob;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJobRepository;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessPublisher;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessRequest;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessType;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessingService;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessingView;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RevalidationServiceTest {

    private final LanguageRegistry languages = mock(LanguageRegistry.class);
    private final ReprocessJobRepository jobs = mock(ReprocessJobRepository.class);
    private final ReprocessPublisher publisher = mock(ReprocessPublisher.class);
    private final ReprocessingService reprocessing = mock(ReprocessingService.class);
    private final RevalidationService service =
            new RevalidationService(languages, jobs, publisher, reprocessing);

    @Test
    void startSavesTheJobAndRequestsItsProcessing() {
        LanguageService language = mock(LanguageService.class);
        when(languages.get("printscript", "1.2")).thenReturn(language);
        when(language.language()).thenReturn("printscript");
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        when(reprocessing.view(any()))
                .thenAnswer(
                        call -> {
                            ReprocessJob job = call.getArgument(0);
                            return new ReprocessingView(
                                    job.getId(),
                                    job.getType(),
                                    job.getLanguage(),
                                    job.getLanguageVersion(),
                                    ReprocessingView.Status.RUNNING,
                                    0,
                                    0,
                                    0,
                                    0,
                                    null);
                        });

        RevalidationRunView view = service.start("printscript", "1.2");

        ArgumentCaptor<ReprocessRequest> request = ArgumentCaptor.forClass(ReprocessRequest.class);
        verify(publisher).request(request.capture());
        assertThat(request.getValue().type()).isEqualTo(ReprocessType.REVALIDATE);
        assertThat(request.getValue().userId()).isNull();
        assertThat(request.getValue().version()).isEqualTo("1.2");
        assertThat(view.id()).isEqualTo(request.getValue().id());
        assertThat(view.version()).isEqualTo("1.2");
    }

    @Test
    void startRejectsAVersionTheLanguageDoesNotSupport() {
        when(languages.get(anyString(), anyString()))
                .thenThrow(new UnsupportedLanguageException("printscript", "9.9"));

        assertThatThrownBy(() -> service.start("printscript", "9.9"))
                .isInstanceOf(UnsupportedLanguageException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void aJobThatCouldNotBeQueuedIsRemoved() {
        LanguageService language = mock(LanguageService.class);
        when(languages.get("printscript", "1.2")).thenReturn(language);
        when(language.language()).thenReturn("printscript");
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("redis down")).when(publisher).request(any());

        assertThatThrownBy(() -> service.start("printscript", "1.2"))
                .isInstanceOf(IllegalStateException.class);

        verify(jobs).delete(any(ReprocessJob.class));
    }

    @Test
    void findReportsProcessedAsDoneAndFailed() {
        UUID id = UUID.randomUUID();
        when(reprocessing.findRevalidation(id))
                .thenReturn(
                        new ReprocessingView(
                                id,
                                ReprocessType.REVALIDATE,
                                "printscript",
                                "1.2",
                                ReprocessingView.Status.COMPLETED,
                                5,
                                3,
                                1,
                                1,
                                null));

        RevalidationRunView view = service.find(id);

        assertThat(view.total()).isEqualTo(5);
        assertThat(view.processed()).isEqualTo(4);
        assertThat(view.failed()).isEqualTo(1);
    }
}
