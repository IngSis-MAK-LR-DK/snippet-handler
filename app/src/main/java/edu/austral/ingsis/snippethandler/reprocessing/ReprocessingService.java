package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.NotFoundException;

import org.springframework.stereotype.Service;

/** Progress of the reprocessing a user triggered by changing their rules. */
@Service
public class ReprocessingService {

    private final ReprocessJobRepository jobs;
    private final ReprocessTaskRepository tasks;

    public ReprocessingService(ReprocessJobRepository jobs, ReprocessTaskRepository tasks) {
        this.jobs = jobs;
        this.tasks = tasks;
    }

    /** The latest ones, newest first. */
    public List<ReprocessingView> list(String userId) {
        return jobs.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    public ReprocessingView find(String userId, UUID id) {
        return jobs.findById(id)
                .filter(job -> job.getUserId().equals(userId))
                .map(this::view)
                .orElseThrow(
                        () -> new NotFoundException("Reprocessing %s not found".formatted(id)));
    }

    private ReprocessingView view(ReprocessJob job) {
        long done = 0;
        long failed = 0;
        long pending = 0;
        for (StatusCount count : tasks.countByStatus(job.getId())) {
            switch (count.status()) {
                case DONE -> done = count.total();
                case FAILED -> failed = count.total();
                case PENDING -> pending = count.total();
            }
        }
        return new ReprocessingView(
                job.getId(),
                job.getType(),
                job.getLanguage(),
                pending == 0 ? ReprocessingView.Status.COMPLETED : ReprocessingView.Status.RUNNING,
                done + failed + pending,
                done,
                failed,
                pending,
                job.getCreatedAt());
    }
}
