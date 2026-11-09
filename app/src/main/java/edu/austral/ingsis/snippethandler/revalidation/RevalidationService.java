package edu.austral.ingsis.snippethandler.revalidation;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJob;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJobRepository;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessPublisher;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessRequest;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessingService;

import org.springframework.stereotype.Service;

/**
 * Starts the revalidation of every snippet of a language when a version of that language is
 * available (US 16 applied to parser versions). It is one more kind of background reprocessing,
 * with the same tasks, retries and progress as a change of rules.
 */
@Service
public class RevalidationService {

    private final LanguageRegistry languages;
    private final ReprocessJobRepository jobs;
    private final ReprocessPublisher publisher;
    private final ReprocessingService reprocessing;

    public RevalidationService(
            LanguageRegistry languages,
            ReprocessJobRepository jobs,
            ReprocessPublisher publisher,
            ReprocessingService reprocessing) {
        this.languages = languages;
        this.jobs = jobs;
        this.publisher = publisher;
        this.reprocessing = reprocessing;
    }

    /**
     * Fails fast if the version is not one the language adapter supports. The job is saved right
     * away so the caller can follow it; its tasks are written in the background.
     */
    public RevalidationRunView start(String language, String version) {
        LanguageService service = languages.get(language, version);
        ReprocessRequest request = ReprocessRequest.revalidation(service.language(), version);
        ReprocessJob job = jobs.save(ReprocessJob.of(request));
        try {
            publisher.request(request);
        } catch (RuntimeException e) {
            jobs.delete(job); // nothing will ever process it
            throw e;
        }
        return RevalidationRunView.of(reprocessing.view(job));
    }

    public RevalidationRunView find(UUID runId) {
        return RevalidationRunView.of(reprocessing.findRevalidation(runId));
    }
}
