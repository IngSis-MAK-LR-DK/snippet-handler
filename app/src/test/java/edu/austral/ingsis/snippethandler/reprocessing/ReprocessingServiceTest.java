package edu.austral.ingsis.snippethandler.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.language.RuleType;

import org.junit.jupiter.api.Test;

class ReprocessingServiceTest {

    private final ReprocessJobRepository jobs = mock(ReprocessJobRepository.class);
    private final ReprocessTaskRepository tasks = mock(ReprocessTaskRepository.class);
    private final ReprocessingService service = new ReprocessingService(jobs, tasks);

    @Test
    void countsTasksByStatusAndIsRunningWhileSomeArePending() {
        ReprocessJob job = job("auth0|ana");
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(tasks.countByStatus(job.getId()))
                .thenReturn(
                        List.of(
                                new StatusCount(TaskStatus.DONE, 7),
                                new StatusCount(TaskStatus.FAILED, 1),
                                new StatusCount(TaskStatus.PENDING, 2)));

        ReprocessingView view = service.find("auth0|ana", job.getId());

        assertThat(view.total()).isEqualTo(10);
        assertThat(view.done()).isEqualTo(7);
        assertThat(view.failed()).isEqualTo(1);
        assertThat(view.pending()).isEqualTo(2);
        assertThat(view.status()).isEqualTo(ReprocessingView.Status.RUNNING);
    }

    @Test
    void isCompletedWhenNothingIsPending() {
        ReprocessJob job = job("auth0|ana");
        when(jobs.findTop20ByUserIdOrderByCreatedAtDesc("auth0|ana")).thenReturn(List.of(job));
        when(tasks.countByStatus(job.getId()))
                .thenReturn(List.of(new StatusCount(TaskStatus.DONE, 3)));

        assertThat(service.list("auth0|ana"))
                .singleElement()
                .extracting(ReprocessingView::status)
                .isEqualTo(ReprocessingView.Status.COMPLETED);
    }

    @Test
    void aJobOfAnotherUserIsNotFound() {
        ReprocessJob job = job("auth0|ana");
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.find("auth0|bob", job.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    private static ReprocessJob job(String userId) {
        return new ReprocessJob(UUID.randomUUID(), userId, RuleType.LINT, "printscript");
    }
}
