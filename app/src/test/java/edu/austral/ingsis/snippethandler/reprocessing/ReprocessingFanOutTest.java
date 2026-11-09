package edu.austral.ingsis.snippethandler.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.snippet.Compliance;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class ReprocessingFanOutTest {

    private static final String USER = "auth0|ana";

    private final ReprocessJobRepository jobs = mock(ReprocessJobRepository.class);
    private final ReprocessTaskRepository tasks = mock(ReprocessTaskRepository.class);
    private final SnippetRepository snippets = mock(SnippetRepository.class);
    private final StreamPublisher streams = mock(StreamPublisher.class);
    private final ReprocessingFanOut fanOut =
            new ReprocessingFanOut(
                    jobs, tasks, snippets, streams, TransactionOperations.withoutTransaction());

    @Test
    void createsATaskPerSnippetAndQueuesThem() {
        ReprocessRequest event =
                ReprocessRequest.rulesChanged(USER, RuleType.FORMAT, "printscript");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(jobs.findById(event.id())).thenReturn(Optional.empty());
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        when(snippets.findIdsByOwnerIdAndLanguage(USER, "printscript"))
                .thenReturn(List.of(first, second));
        ReprocessTask a = task(event.id(), first);
        ReprocessTask b = task(event.id(), second);
        when(tasks.findAllByJobIdAndStatus(event.id(), TaskStatus.PENDING))
                .thenReturn(List.of(a, b));

        fanOut.handle(event.toFields());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReprocessTask>> saved = ArgumentCaptor.forClass(List.class);
        verify(tasks).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(ReprocessTask::getSnippetId)
                .containsExactly(first, second);
        verify(streams).publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", a.getId().toString()));
        verify(streams).publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", b.getId().toString()));
        verify(snippets, never()).markCompliance(any(), any(), any());
    }

    @Test
    void lintMarksTheSnippetsAsPendingUntilTheyAreRechecked() {
        ReprocessRequest event = ReprocessRequest.rulesChanged(USER, RuleType.LINT, "printscript");
        when(jobs.findById(event.id())).thenReturn(Optional.empty());
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        when(snippets.findIdsByOwnerIdAndLanguage(USER, "printscript")).thenReturn(List.of());

        fanOut.handle(event.toFields());

        verify(snippets).markCompliance(USER, "printscript", Compliance.PENDING);
    }

    @Test
    void aRevalidationTakesTheSnippetsOfEveryOwner() {
        ReprocessRequest request = ReprocessRequest.revalidation("printscript", "1.2");
        ReprocessJob job = ReprocessJob.of(request);
        when(jobs.findById(request.id())).thenReturn(Optional.of(job));
        UUID id = UUID.randomUUID();
        when(snippets.findIdsByLanguage("printscript")).thenReturn(List.of(id));

        fanOut.handle(request.toFields());

        verify(snippets, never()).findIdsByOwnerIdAndLanguage(any(), any());
        verify(snippets, never()).markCompliance(any(), any(), any());
        assertThat(job.isExpanded()).isTrue();
        verify(tasks).saveAll(any());
    }

    @Test
    void aRedeliveredEventOnlyQueuesWhatIsStillPending() {
        ReprocessRequest event =
                ReprocessRequest.rulesChanged(USER, RuleType.FORMAT, "printscript");
        ReprocessJob existing = ReprocessJob.of(event);
        existing.markExpanded();
        when(jobs.findById(event.id())).thenReturn(Optional.of(existing));
        ReprocessTask left = task(event.id(), UUID.randomUUID());
        when(tasks.findAllByJobIdAndStatus(event.id(), TaskStatus.PENDING))
                .thenReturn(List.of(left));

        fanOut.handle(event.toFields());

        verify(tasks, never()).saveAll(any());
        verify(streams, times(1)).publish(eq(Streams.SNIPPET_REPROCESS), any());
    }

    private static ReprocessTask task(UUID jobId, UUID snippetId) {
        ReprocessTask task = new ReprocessTask(jobId, snippetId);
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        return task;
    }
}
