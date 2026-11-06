package edu.austral.ingsis.snippethandler.revalidation;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.revalidation.RevalidationRun.RunStatus;

public record RevalidationRunView(
        UUID id,
        String language,
        String version,
        RunStatus status,
        int total,
        int processed,
        int failed) {

    public static RevalidationRunView of(RevalidationRun run) {
        return new RevalidationRunView(
                run.getId(),
                run.getLanguage(),
                run.getLanguageVersion(),
                run.getStatus(),
                run.getTotal(),
                run.getProcessed(),
                run.getFailed());
    }
}
