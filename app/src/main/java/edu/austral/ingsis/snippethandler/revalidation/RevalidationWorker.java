package edu.austral.ingsis.snippethandler.revalidation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.ValidationResult;
import edu.austral.ingsis.snippethandler.snippet.LintReport;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.snippet.SnippetStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Revalidates snippets one by one. A snippet that fails does not stop the run: it is counted and
 * the next one is processed. Progress is saved after every snippet so it can be followed.
 */
@Component
public class RevalidationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RevalidationWorker.class);

    private final RevalidationRunRepository runs;
    private final SnippetRepository snippets;
    private final SnippetContents contents;
    private final SnippetStyle style;
    private final LanguageRegistry languages;

    public RevalidationWorker(
            RevalidationRunRepository runs,
            SnippetRepository snippets,
            SnippetContents contents,
            SnippetStyle style,
            LanguageRegistry languages) {
        this.runs = runs;
        this.snippets = snippets;
        this.contents = contents;
        this.style = style;
        this.languages = languages;
    }

    @Async
    public void revalidate(UUID runId, List<UUID> snippetIds, String language, String version) {
        RevalidationRun run = runs.findById(runId).orElseThrow();
        for (UUID snippetId : snippetIds) {
            boolean failed = false;
            try {
                revalidateOne(snippetId, language, version);
            } catch (RuntimeException e) {
                LOG.warn(
                        "Could not revalidate snippet {} with {} {}",
                        snippetId,
                        language,
                        version,
                        e);
                failed = true;
            }
            run.advance(failed);
            runs.save(run);
        }
        run.complete();
        runs.save(run);
    }

    /**
     * A snippet valid in the new version moves to it and gets the new lint result. One that is not
     * valid keeps its version and is marked {@code FAILED}.
     */
    void revalidateOne(UUID snippetId, String language, String version) {
        Optional<Snippet> found = snippets.findById(snippetId);
        if (found.isEmpty()) {
            return; // deleted while the run was waiting
        }
        Snippet snippet = found.get();
        LanguageService service = languages.get(language, version);
        String content = contents.read(snippetId);
        ValidationResult validation = service.validate(content, version);
        if (validation.valid()) {
            LintReport report = style.lint(snippet.getOwnerId(), service, version, content);
            snippet.changeLanguage(service.language(), version);
            snippet.linted(report);
        } else {
            snippet.linted(LintReport.failed());
        }
        snippets.save(snippet);
    }
}
