package edu.austral.ingsis.snippethandler.revalidation;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJob;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessJobRepository;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessPublisher;
import edu.austral.ingsis.snippethandler.reprocessing.ReprocessRequest;

import org.springframework.stereotype.Service;

/**
 * Revalidates every snippet of a language against its newest version, when a new adapter of that
 * language is deployed ({@link AdapterVersionWatcher}). It is one more kind of background
 * reprocessing, with the same tasks, retries and progress as a change of rules.
 */
@Service
public class RevalidationService {

    private final LanguageRegistry languages;
    private final ReprocessJobRepository jobs;
    private final ReprocessPublisher publisher;

    public RevalidationService(
            LanguageRegistry languages, ReprocessJobRepository jobs, ReprocessPublisher publisher) {
        this.languages = languages;
        this.jobs = jobs;
        this.publisher = publisher;
    }

    /**
     * Fails fast if the version is not one the language adapter supports. The job is saved right
     * away and its tasks are written in the background.
     */
    public UUID start(String language, String version) {
        LanguageService service = languages.get(language, version);
        ReprocessRequest request = ReprocessRequest.revalidation(service.language(), version);
        ReprocessJob job = jobs.save(ReprocessJob.of(request));
        try {
            publisher.request(request);
        } catch (RuntimeException e) {
            jobs.delete(job); // nothing will ever process it
            throw e;
        }
        return job.getId();
    }
}
