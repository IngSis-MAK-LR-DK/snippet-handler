package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.snippet.Compliance;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reacts to a {@link ReprocessRequest}: makes sure its job exists, writes a task per snippet to
 * process (status {@code PENDING}) and queues each one on the {@link Streams#SNIPPET_REPROCESS}
 * stream. Receiving the same request twice is harmless: the job and its tasks already exist and
 * only the unfinished tasks are queued again.
 */
@Component
public class ReprocessingFanOut {

    private final ReprocessJobRepository jobs;
    private final ReprocessTaskRepository tasks;
    private final SnippetRepository snippets;
    private final StreamPublisher streams;
    private final TransactionOperations transaction;

    public ReprocessingFanOut(
            ReprocessJobRepository jobs,
            ReprocessTaskRepository tasks,
            SnippetRepository snippets,
            StreamPublisher streams,
            TransactionOperations transaction) {
        this.jobs = jobs;
        this.tasks = tasks;
        this.snippets = snippets;
        this.streams = streams;
        this.transaction = transaction;
    }

    public void handle(Map<String, String> fields) {
        ReprocessRequest request = ReprocessRequest.from(fields);
        transaction.executeWithoutResult(status -> createTasks(request));
        for (ReprocessTask task : tasks.findAllByJobIdAndStatus(request.id(), TaskStatus.PENDING)) {
            streams.publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", task.getId().toString()));
        }
    }

    private void createTasks(ReprocessRequest request) {
        // A revalidation job is created when it is requested, so the caller already has its id.
        ReprocessJob job =
                jobs.findById(request.id()).orElseGet(() -> jobs.save(ReprocessJob.of(request)));
        if (job.isExpanded()) {
            return;
        }
        List<UUID> ids =
                request.type() == ReprocessType.REVALIDATE
                        ? snippets.findIdsByLanguage(request.language())
                        : snippets.findIdsByOwnerIdAndLanguage(
                                request.userId(), request.language());
        tasks.saveAll(ids.stream().map(id -> new ReprocessTask(request.id(), id)).toList());
        if (request.type() == ReprocessType.LINT) {
            // The user sees which snippets are still waiting for their new lint result.
            snippets.markCompliance(request.userId(), request.language(), Compliance.PENDING);
        }
        job.markExpanded();
        jobs.save(job);
    }
}
