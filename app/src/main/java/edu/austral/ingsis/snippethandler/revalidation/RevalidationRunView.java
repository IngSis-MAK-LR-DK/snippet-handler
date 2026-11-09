package edu.austral.ingsis.snippethandler.revalidation;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.reprocessing.ReprocessingView;

/** Progress of a revalidation by language version; {@code processed} includes the failed ones. */
public record RevalidationRunView(
        UUID id,
        String language,
        String version,
        ReprocessingView.Status status,
        long total,
        long processed,
        long failed) {

    public static RevalidationRunView of(ReprocessingView job) {
        return new RevalidationRunView(
                job.id(),
                job.language(),
                job.version(),
                job.status(),
                job.total(),
                job.done() + job.failed(),
                job.failed());
    }
}
