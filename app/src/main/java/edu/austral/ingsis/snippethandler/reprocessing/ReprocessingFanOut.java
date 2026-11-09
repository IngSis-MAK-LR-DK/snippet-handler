package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.snippet.Compliance;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reacts to {@link RulesChanged}: writes a task per snippet of the user (status {@code PENDING})
 * and queues each one on the {@link Streams#SNIPPET_REPROCESS} stream. Receiving the same message
 * twice is harmless: the job already exists and only the unfinished tasks are queued again.
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
        RulesChanged event = RulesChanged.from(fields);
        if (!jobs.existsById(event.id())) {
            transaction.executeWithoutResult(status -> createJob(event));
        }
        for (ReprocessTask task : tasks.findAllByJobIdAndStatus(event.id(), TaskStatus.PENDING)) {
            streams.publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", task.getId().toString()));
        }
    }

    private void createJob(RulesChanged event) {
        jobs.save(new ReprocessJob(event.id(), event.userId(), event.type(), event.language()));
        List<UUID> ids = snippets.findIdsByOwnerIdAndLanguage(event.userId(), event.language());
        tasks.saveAll(ids.stream().map(id -> new ReprocessTask(event.id(), id)).toList());
        if (event.type() == RuleType.LINT) {
            // The user sees which snippets are still waiting for their new lint result.
            snippets.markCompliance(event.userId(), event.language(), Compliance.PENDING);
        }
    }
}
