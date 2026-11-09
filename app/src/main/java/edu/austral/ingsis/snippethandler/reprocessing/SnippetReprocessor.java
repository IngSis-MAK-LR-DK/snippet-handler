package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.snippet.LintReport;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.snippet.SnippetStore;
import edu.austral.ingsis.snippethandler.snippet.SnippetStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Processes one snippet of a job: lints it (US 15), formats it (US 12) or revalidates it against a
 * new version of its language. A task is tried up to {@code maxAttempts} times; after that the
 * snippet is skipped, counted as failed, and the rest of the job goes on. Handling a finished task
 * does nothing, so a message delivered twice is harmless.
 */
@Component
public class SnippetReprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetReprocessor.class);

    private final ReprocessTaskRepository tasks;
    private final ReprocessJobRepository jobs;
    private final SnippetRepository snippets;
    private final SnippetContents contents;
    private final SnippetStore store;
    private final SnippetStyle style;
    private final LanguageRegistry languages;
    private final int maxAttempts;

    public SnippetReprocessor(
            ReprocessTaskRepository tasks,
            ReprocessJobRepository jobs,
            SnippetRepository snippets,
            SnippetContents contents,
            SnippetStore store,
            SnippetStyle style,
            LanguageRegistry languages,
            ReprocessingProperties properties) {
        this.tasks = tasks;
        this.jobs = jobs;
        this.snippets = snippets;
        this.contents = contents;
        this.store = store;
        this.style = style;
        this.languages = languages;
        this.maxAttempts = properties.maxAttempts();
    }

    /** Throws if the attempt failed and there are tries left, so the message comes back later. */
    public void handle(Map<String, String> fields) {
        Optional<ReprocessTask> found = tasks.findById(UUID.fromString(fields.get("taskId")));
        if (found.isEmpty() || found.get().getStatus() != TaskStatus.PENDING) {
            return;
        }
        ReprocessTask task = found.get();
        ReprocessJob job = jobs.findById(task.getJobId()).orElseThrow();
        if (task.getAttempts() >= maxAttempts) {
            // Earlier attempts never reported back (the process died): give up on this snippet.
            giveUp(task, job, "no result after " + task.getAttempts() + " attempts");
            return;
        }
        task.attempt();
        tasks.saveAndFlush(task);
        try {
            reprocess(task.getSnippetId(), job);
        } catch (RuntimeException e) {
            LOG.warn(
                    "Attempt {}/{} on snippet {} failed",
                    task.getAttempts(),
                    maxAttempts,
                    task.getSnippetId(),
                    e);
            if (task.getAttempts() >= maxAttempts) {
                giveUp(task, job, String.valueOf(e.getMessage()));
                return;
            }
            task.recordError(String.valueOf(e.getMessage()));
            tasks.save(task);
            throw e;
        }
        task.done();
        tasks.save(task);
    }

    private void reprocess(UUID snippetId, ReprocessJob job) {
        Snippet snippet = snippets.findById(snippetId).orElse(null);
        if (snippet == null) {
            return; // deleted while it was waiting
        }
        switch (job.getType()) {
            case LINT -> lint(snippet);
            case FORMAT -> format(snippet);
            case REVALIDATE -> revalidate(snippet, job);
        }
    }

    private void lint(Snippet snippet) {
        LanguageService language = languages.get(snippet.getLanguage());
        String version = snippet.getLanguageVersion();
        String content = contents.read(snippet.getId());
        snippet.linted(style.lintStrict(snippet.getOwnerId(), language, version, content));
        snippets.save(snippet);
    }

    private void format(Snippet snippet) {
        LanguageService language = languages.get(snippet.getLanguage());
        String version = snippet.getLanguageVersion();
        String content = contents.read(snippet.getId());
        String formatted = style.format(snippet.getOwnerId(), language, version, content);
        if (!formatted.equals(content)) {
            snippet.linted(style.lint(snippet.getOwnerId(), language, version, formatted));
            store.save(snippet, formatted, content, s -> {});
        }
    }

    /**
     * A snippet valid in the new version moves to it and gets the new lint result. One that is not
     * valid keeps its version and is marked {@code FAILED}.
     */
    private void revalidate(Snippet snippet, ReprocessJob job) {
        String version = job.getLanguageVersion();
        LanguageService language = languages.get(job.getLanguage(), version);
        String content = contents.read(snippet.getId());
        if (language.validate(content, version).valid()) {
            LintReport report = style.lintStrict(snippet.getOwnerId(), language, version, content);
            snippet.changeLanguage(language.language(), version);
            snippet.linted(report);
        } else {
            snippet.linted(LintReport.failed());
        }
        snippets.save(snippet);
    }

    private void giveUp(ReprocessTask task, ReprocessJob job, String reason) {
        task.failed(reason);
        tasks.save(task);
        if (job.getType() == ReprocessType.LINT) {
            // The snippet was left waiting for its lint result: say it could not be checked.
            snippets.findById(task.getSnippetId())
                    .ifPresent(
                            snippet -> {
                                snippet.linted(LintReport.failed());
                                snippets.save(snippet);
                            });
        }
        LOG.error("Skipping snippet {}: {}", task.getSnippetId(), reason);
    }
}
