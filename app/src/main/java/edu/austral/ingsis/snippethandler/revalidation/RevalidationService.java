package edu.austral.ingsis.snippethandler.revalidation;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.error.NotFoundException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;

import org.springframework.stereotype.Service;

/**
 * Starts the revalidation of every snippet of a language when a version of that language is
 * available (US 16 applied to parser versions). The work itself runs in the background.
 */
@Service
public class RevalidationService {

    private final LanguageRegistry languages;
    private final SnippetRepository snippets;
    private final RevalidationRunRepository runs;
    private final RevalidationWorker worker;

    public RevalidationService(
            LanguageRegistry languages,
            SnippetRepository snippets,
            RevalidationRunRepository runs,
            RevalidationWorker worker) {
        this.languages = languages;
        this.snippets = snippets;
        this.runs = runs;
        this.worker = worker;
    }

    /** Fails fast if the version is not one the language adapter supports. */
    public RevalidationRunView start(String language, String version) {
        LanguageService service = languages.get(language, version);
        List<UUID> ids = snippets.findIdsByLanguage(service.language());
        RevalidationRun run =
                runs.save(new RevalidationRun(service.language(), version, ids.size()));
        worker.revalidate(run.getId(), ids, service.language(), version);
        return RevalidationRunView.of(run);
    }

    public RevalidationRunView find(UUID runId) {
        return runs.findById(runId)
                .map(RevalidationRunView::of)
                .orElseThrow(
                        () -> new NotFoundException("Revalidation %s not found".formatted(runId)));
    }
}
